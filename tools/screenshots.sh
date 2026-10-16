#!/usr/bin/env bash
# Dev tool: renders the CNC Lathe in a real (headless) Minecraft client and saves screenshots to
# run/screenshots/. Needs Xvfb and Mesa (software OpenGL) on Linux. Run from the repo root.
#   tools/screenshots.sh
set -euo pipefail
mkdir -p run run/saves
echo "eula=true" > run/eula.txt

# 1. A superflat world for the scene (made once by the dedicated server, then reused)
if [ ! -d run/saves/cnc_scene ]; then
  cat > run/server.properties <<PROPS
level-name=cnc_scene
level-type=minecraft\:flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
online-mode=false
PROPS
  ./gradlew runServer --console=plain > run/scene-server.log 2>&1 &
  until grep -q 'Done (' run/scene-server.log 2>/dev/null; do sleep 2; done
  pkill -TERM -f 'fml.modFolders=create_machining.*net.neoforged.*server' || true
  pkill -TERM -f 'neoforge.*--nogui' || true
  sleep 10
  mv run/cnc_scene run/saves/cnc_scene
fi

# 2. Client settings: skip first-run screens, no sound
printf 'onboardAccessibility:false\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:6\nsoundCategory_master:0.0\n' > run/options.txt

# 3. Film it (ScreenshotDirector does the scripting, then quits the game)
rm -f run/screenshots/*.png
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runScreenshotClient --console=plain > run/scene-client.log 2>&1 || true
ls run/screenshots/
