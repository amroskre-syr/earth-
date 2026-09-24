# Earth rotation video input

This renderer does not redraw the supplied artwork. It composites the original assets into a loop.

Required:
- `assets/earth_frames/` — ordered original Earth rotation frames.
- `assets/background.jpg` — original clean space background.

Optional:
- `assets/clouds.png` — original transparent cloud layer. When supplied it counter-rotates at gently randomized speeds.

Output: `output/earth_rotation.mp4`, 30 fps, 12 seconds.

The background moves in the same direction as Earth at 10% apparent speed to create distance/parallax.
