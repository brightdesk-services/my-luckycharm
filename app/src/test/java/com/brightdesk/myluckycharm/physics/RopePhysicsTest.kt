package com.brightdesk.myluckycharm.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

private const val ANCHOR_X = 500f
private const val ANCHOR_Y = 100f
private const val SEGMENT = 40f
private const val CHARM_RADIUS = 60f
private const val ROPE_LENGTH = 280f
private val GRAVITY = Vec2(0f, 2f)

class RopePhysicsTest {

    private fun newRope() = RopePhysics(segmentLength = SEGMENT).apply { resetTo(ANCHOR_X, ANCHOR_Y) }

    private fun RopePhysics.settle(steps: Int = 4000) {
        repeat(steps) { step(ANCHOR_X, ANCHOR_Y, GRAVITY, null) }
    }

    @Test
    fun `rope returns to hanging straight down after being displaced`() {
        val rope = newRope()
        rope.beginDrag()
        repeat(60) { rope.step(ANCHOR_X, ANCHOR_Y, GRAVITY, Vec2(ANCHOR_X + 250f, ANCHOR_Y + 60f)) }
        rope.endDrag()
        rope.settle()

        assertEquals(ANCHOR_X, rope.charm.x, 1f)
        // Six relaxation iterations trade exactness for speed, so a rope under
        // constant gravity settles a couple of percent past its nominal length.
        val hang = rope.charm.y - ANCHOR_Y
        assertTrue("hung $hang, expected about ${rope.maxLength}", hang >= rope.maxLength - 1f)
        assertTrue("hung $hang, expected about ${rope.maxLength}", hang <= rope.maxLength * 1.05f)
    }

    @Test
    fun `segments hold their length while swinging`() {
        val rope = newRope()
        rope.beginDrag()
        repeat(30) { rope.step(ANCHOR_X, ANCHOR_Y, GRAVITY, Vec2(ANCHOR_X + 200f, ANCHOR_Y + 200f)) }
        rope.endDrag()

        repeat(200) {
            rope.step(ANCHOR_X, ANCHOR_Y, GRAVITY, null)
            for (index in 0 until rope.points.lastIndex) {
                val a = rope.points[index]
                val b = rope.points[index + 1]
                val length = hypot(b.x - a.x, b.y - a.y)
                assertTrue("segment $index stretched to $length", abs(length - SEGMENT) < SEGMENT * 0.25f)
            }
        }
    }

    @Test
    fun `anchor stays pinned`() {
        val rope = newRope()
        rope.beginDrag()
        repeat(100) { rope.step(ANCHOR_X, ANCHOR_Y, GRAVITY, Vec2(900f, 700f)) }
        rope.endDrag()
        rope.settle(200)

        assertEquals(ANCHOR_X, rope.points[0].x, 0.001f)
        assertEquals(ANCHOR_Y, rope.points[0].y, 0.001f)
    }

    @Test
    fun `release imparts the drag velocity, not just the position`() {
        // Both ropes are released from the same point, well within reach so the
        // length constraint doesn't dominate. Only the swept one should carry
        // velocity out of the release.
        val holdX = ANCHOR_X + 90f
        val holdY = ANCHOR_Y + 200f

        val swept = newRope()
        swept.beginDrag()
        repeat(40) { tick ->
            swept.step(ANCHOR_X, ANCHOR_Y, GRAVITY, Vec2(holdX - (39 - tick) * 3f, holdY))
        }
        swept.endDrag()

        val held = newRope()
        held.beginDrag()
        repeat(40) { held.step(ANCHOR_X, ANCHOR_Y, GRAVITY, Vec2(holdX, holdY)) }
        held.endDrag()

        assertEquals("released from the same point", held.charm.x, swept.charm.x, 0.01f)

        repeat(5) {
            swept.step(ANCHOR_X, ANCHOR_Y, GRAVITY, null)
            held.step(ANCHOR_X, ANCHOR_Y, GRAVITY, null)
        }

        assertTrue(
            "swept=${swept.charm.x} held=${held.charm.x}",
            swept.charm.x > held.charm.x + 5f,
        )
    }
}

class PullLimitTest {

    private val bounds = Bounds(0f, 0f, 1080f, 2400f)

    @Test
    fun `charm never leaves the viewport`() {
        val anchorX = 540f
        val anchorY = 60f
        val farTargets = listOf(
            Vec2(-5000f, 0f),
            Vec2(5000f, 0f),
            Vec2(0f, -5000f),
            Vec2(5000f, 5000f),
            Vec2(-5000f, 5000f),
        )

        for (target in farTargets) {
            val clamped = PullLimit.clampTarget(
                anchorX, anchorY, target.x, target.y, bounds, CHARM_RADIUS, ROPE_LENGTH,
            )
            assertTrue("left edge: $clamped", clamped.x >= bounds.left + CHARM_RADIUS - 0.01f)
            assertTrue("right edge: $clamped", clamped.x <= bounds.right - CHARM_RADIUS + 0.01f)
            assertTrue("top edge: $clamped", clamped.y >= bounds.top + CHARM_RADIUS - 0.01f)
            assertTrue("bottom edge: $clamped", clamped.y <= bounds.bottom - CHARM_RADIUS + 0.01f)
        }
    }

    @Test
    fun `the side inset follows the horizontal radius, not the vertical one`() {
        // The charm hangs below its attach point but straddles it sideways, so
        // the left and right edges are inset by half what the bottom is. Before
        // this was split out, the anchor stopped a whole charm short of each
        // side of the screen instead of half of one.
        val halfWidth = CHARM_RADIUS / 2f
        val anchorY = 600f

        val left = PullLimit.clampTarget(
            halfWidth, anchorY, -5000f, anchorY, bounds, CHARM_RADIUS, ROPE_LENGTH,
            charmRadiusX = halfWidth,
        )
        assertEquals("should reach the half-width inset", bounds.left + halfWidth, left.x, 0.01f)

        val right = PullLimit.clampTarget(
            bounds.right - halfWidth, anchorY, 5000f, anchorY, bounds, CHARM_RADIUS, ROPE_LENGTH,
            charmRadiusX = halfWidth,
        )
        assertEquals("should reach the half-width inset", bounds.right - halfWidth, right.x, 0.01f)

        // The bottom is untouched: it still uses the full drop.
        val down = PullLimit.clampTarget(
            540f, anchorY, 540f, 99999f, bounds, CHARM_RADIUS, 99999f,
            charmRadiusX = halfWidth,
        )
        assertEquals("bottom still full radius", bounds.bottom - CHARM_RADIUS, down.y, 0.01f)
    }

    @Test
    fun `an anchor parked on the side inset still swings down, not up`() {
        // The anchor's own clamp parks it exactly on the inset at either edge,
        // so a pull aimed outward has zero room left sideways. It must keep the
        // rest of the pull and slide down the edge: scaling the whole direction
        // by one ray-cast used to zero the downward component along with the
        // sideways one, collapsing the charm onto the anchor, where the bunched
        // rope span the art around to hang upside down.
        val halfWidth = CHARM_RADIUS / 2f
        val anchorY = 300f

        for (anchorX in listOf(bounds.left + halfWidth, bounds.right - halfWidth)) {
            val outward = if (anchorX < bounds.right / 2f) -4000f else 4000f
            for (target in listOf(
                Vec2(outward, anchorY + 200f),   // outward and down
                Vec2(outward, anchorY),          // straight out at the edge
                Vec2(outward, anchorY - 500f),   // outward and up, flattened
            )) {
                val clamped = PullLimit.clampTarget(
                    anchorX, anchorY, target.x, target.y, bounds, CHARM_RADIUS, ROPE_LENGTH,
                    charmRadiusX = halfWidth,
                )
                assertTrue("must never rise above the anchor: $clamped", clamped.y >= anchorY - 0.01f)
                assertTrue("must stay on screen: $clamped", clamped.x >= bounds.left + halfWidth - 0.01f)
                assertTrue("must stay on screen: $clamped", clamped.x <= bounds.right - halfWidth + 0.01f)
            }
        }

        // The case that actually regressed: a 45-degree pull out and down from
        // an anchor already hard against the right inset. The sideways half has
        // nowhere to go, so the charm should end up on the edge and most of the
        // rope's length below the anchor. It used to come back pinned at exactly
        // the anchor, which is what span the art upside down.
        val anchorX = bounds.right - halfWidth
        val pinned = PullLimit.clampTarget(
            anchorX, anchorY, anchorX + 300f, anchorY + 300f,
            bounds, CHARM_RADIUS, ROPE_LENGTH, charmRadiusX = halfWidth,
        )
        assertEquals("should sit on the edge", anchorX, pinned.x, 0.01f)
        assertTrue("should drop most of the rope, got $pinned", pinned.y > anchorY + 200f)
    }

    /** Straight-down pull from a spot where the viewport edge is far out of play. */
    private fun reach(pull: Float): Float {
        val anchorX = 540f
        val anchorY = 600f
        val clamped = PullLimit.clampTarget(
            anchorX, anchorY, anchorX, anchorY + pull, bounds, CHARM_RADIUS, ROPE_LENGTH,
        )
        return hypot(clamped.x - anchorX, clamped.y - anchorY)
    }

    @Test
    fun `pulling past the rope length stretches, but resists`() {
        val overpull = 100f
        val reached = reach(ROPE_LENGTH + overpull)
        assertTrue("should give a little, got $reached", reached > ROPE_LENGTH)
        assertTrue("should resist, got $reached", reached < ROPE_LENGTH + overpull)
    }

    @Test
    fun `stretch is bounded however hard it is pulled`() {
        val ceiling = ROPE_LENGTH + ROPE_LENGTH * PullLimit.DEFAULT_STRETCH_FRACTION
        assertTrue("ran away to ${reach(ROPE_LENGTH + 100_000f)}", reach(ROPE_LENGTH + 100_000f) <= ceiling + 0.01f)
    }

    @Test
    fun `a smaller stretch setting gives less travel`() {
        fun reachWith(fraction: Float): Float {
            val clamped = PullLimit.clampTarget(
                540f, 600f, 540f, 600f + 5000f, bounds, CHARM_RADIUS, ROPE_LENGTH, fraction,
            )
            return hypot(clamped.x - 540f, clamped.y - 600f)
        }
        assertEquals("no stretch means a hard stop at the rope's length", ROPE_LENGTH, reachWith(0f), 0.01f)
        assertTrue(reachWith(0.3f) < reachWith(1.0f))
    }

    @Test
    fun `the charm is never lifted above its anchor`() {
        val anchorX = 540f
        val anchorY = 600f
        val upward = listOf(
            Vec2(anchorX, anchorY - 500f),
            Vec2(anchorX + 300f, anchorY - 300f),
            Vec2(anchorX - 300f, anchorY - 300f),
            Vec2(anchorX - 4000f, anchorY - 4000f),
        )
        for (target in upward) {
            val clamped = PullLimit.clampTarget(
                anchorX, anchorY, target.x, target.y, bounds, CHARM_RADIUS, ROPE_LENGTH,
            )
            assertTrue("rose above the anchor: $clamped", clamped.y >= anchorY - 0.01f)
        }
    }

    @Test
    fun `an upward pull flattens into a sideways swing`() {
        val anchorX = 540f
        val anchorY = 600f
        val clamped = PullLimit.clampTarget(
            anchorX, anchorY, anchorX + 200f, anchorY - 400f, bounds, CHARM_RADIUS, ROPE_LENGTH,
        )
        assertEquals("swings out level with the anchor", anchorY, clamped.y, 0.01f)
        assertTrue("stays on the side it was pulled toward", clamped.x > anchorX)
    }

    @Test
    fun `harder pulls stretch further`() {
        assertTrue(reach(ROPE_LENGTH + 40f) < reach(ROPE_LENGTH + 200f))
        assertTrue(reach(ROPE_LENGTH + 200f) < reach(ROPE_LENGTH + 900f))
    }

    @Test
    fun `the stretch curve is smooth across the rope limit`() {
        // Slope at the limit is 1, so a tiny overpull moves almost one-for-one:
        // no perceptible snag at the point resistance begins.
        assertEquals(ROPE_LENGTH, reach(ROPE_LENGTH), 0.01f)
        assertEquals(ROPE_LENGTH + 2f, reach(ROPE_LENGTH + 2f), 0.15f)
    }

    @Test
    fun `a target within reach is left untouched`() {
        val anchorX = 540f
        val anchorY = 600f
        val clamped = PullLimit.clampTarget(
            anchorX, anchorY, anchorX + 100f, anchorY + 100f, bounds, CHARM_RADIUS, ROPE_LENGTH,
        )
        assertEquals(anchorX + 100f, clamped.x, 0.01f)
        assertEquals(anchorY + 100f, clamped.y, 0.01f)
    }

    // The pull is measured from the finger, which starts wherever it grabbed
    // and sweeps the control over a fixed throw.
    private val startY = 600f
    private val travel = 620f

    @Test
    fun `at rest nothing is being pulled`() {
        assertEquals(0f, PullLimit.pullOffsetFraction(startY, startY, travel), 0.001f)
    }

    @Test
    fun `dragging downward is a positive offset, upward negative`() {
        assertTrue(PullLimit.pullOffsetFraction(startY + 100f, startY, travel) > 0f)
        assertTrue(PullLimit.pullOffsetFraction(startY - 100f, startY, travel) < 0f)
    }

    @Test
    fun `halfway down the throw reads a half`() {
        assertEquals(0.5f, PullLimit.pullOffsetFraction(startY + travel / 2f, startY, travel), 0.001f)
    }

    @Test
    fun `halfway up the throw reads a negative half`() {
        assertEquals(-0.5f, PullLimit.pullOffsetFraction(startY - travel / 2f, startY, travel), 0.001f)
    }

    @Test
    fun `dragging past the throw saturates at plus or minus one`() {
        assertEquals(1f, PullLimit.pullOffsetFraction(startY + travel * 5f, startY, travel), 0.001f)
        assertEquals(-1f, PullLimit.pullOffsetFraction(startY - travel * 5f, startY, travel), 0.001f)
    }

    @Test
    fun `a swing stays inside the deadzone, a real pull clears it either way`() {
        // The deadzone is what stops a grab from nudging brightness or volume
        // the instant the charm is touched, so it must swallow a couple of
        // pixels of settling wobble and nothing more, in either direction.
        val nudgeDown = PullLimit.pullOffsetFraction(startY + 2f, startY, travel)
        val nudgeUp = PullLimit.pullOffsetFraction(startY - 2f, startY, travel)
        assertTrue("a couple of pixels must not engage the control", nudgeDown <= PullLimit.PULL_DEADZONE)
        assertTrue("a couple of pixels must not engage the control", -nudgeUp <= PullLimit.PULL_DEADZONE)

        val pulledDown = PullLimit.pullOffsetFraction(startY + travel * 0.1f, startY, travel)
        val pulledUp = PullLimit.pullOffsetFraction(startY - travel * 0.1f, startY, travel)
        assertTrue("a tenth of the throw is a deliberate pull", pulledDown > PullLimit.PULL_DEADZONE)
        assertTrue("a tenth of the throw is a deliberate pull", -pulledUp > PullLimit.PULL_DEADZONE)
    }

    @Test
    fun `a throw of zero cannot divide by it`() {
        assertEquals(0f, PullLimit.pullOffsetFraction(startY + 50f, startY, 0f), 0.001f)
    }

    @Test
    fun `a narrow viewport stops sideways pull before the rope would`() {
        // The edge is a hard stop, so it wins over the rope's stretchy limit.
        val narrow = Bounds(0f, 0f, 400f, 2400f)
        val anchorX = 200f
        val anchorY = 600f
        val clamped = PullLimit.clampTarget(
            anchorX, anchorY, anchorX + 5000f, anchorY, narrow, CHARM_RADIUS, ROPE_LENGTH,
        )
        assertEquals("stops a charm radius short of the edge", 340f, clamped.x, 0.01f)
        assertEquals(anchorY, clamped.y, 0.01f)
    }
}
