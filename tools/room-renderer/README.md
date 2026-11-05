# Room renderer

Makes the photographed rooms used by the "View in a room" preview (`frontend/resources/js/Components/Shop/RoomStage.js`).

The rooms are 3D scenes built to real sizes in inches (`scenes.py`: an 86 in sofa, an 80 in console, a 64 in queen bed, …)
rendered by a small physically based path tracer (`pt.py`: area lights, sun and sky through windows, soft shadows, glossy
floors, three light bounces) with one fixed camera: 50 in high, 230 in from the back wall, 38° vertical field of view. Because
the camera is known, so is the scale: `pxPerInch` photo pixels per inch on the back wall, which is what lets the app hang an
artwork at its true size.

`render_room.py <room> 1200 800 <samples> final` writes for one room:

- `<room>.jpg`: the photo (denoised with an edge-aware à-trous filter, ACES tone mapping, exposure set on the wall where art hangs)
- `<room>-front.png/.webp`: everything standing in front of the back wall (from the depth pass), with alpha
- `<room>.json`: the calibration (wall rectangle, floor line, hanging line, main light direction) and `<room>-raw.npy` (all passes)

`publish.py` turns `final/` into the web assets in `src/main/resources/static/assets/rooms/` (photo, thumbnail, furniture
cut-out, the wall's light as `-shade.jpg` and `-glow.jpg` for blending onto the artwork) and regenerates
`frontend/resources/js/Components/Shop/rooms.js`. Rebuild the React bundle afterwards.

```bash
pip install numpy opencv-python
./render_all.sh            # all four rooms, then publish
```

`final/` and `*.npy` are build output: don't commit them.
