#!/bin/bash
# Renders the four rooms (1200 x 800, 16 samples per pixel) into ./final, two at a time, then publishes them to the app.
# Needs Python 3 with numpy and opencv-python. About 20-30 minutes per room on a laptop CPU.
set -e
cd "$(dirname "$0")"
mkdir -p final
SPP=${SPP:-16}
( python3 render_room.py living 1200 800 $SPP final > final/living.log 2>&1; python3 render_room.py bedroom 1200 800 $SPP final > final/bedroom.log 2>&1 ) &
A=$!
( python3 render_room.py studio 1200 800 $SPP final > final/studio.log 2>&1; python3 render_room.py gallery 1200 800 $SPP final > final/gallery.log 2>&1 ) &
B=$!
wait $A $B
python3 publish.py
