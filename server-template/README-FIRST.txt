VanillaRPG portable server template - target: Minecraft/Paper 26.2, Java 25

1) Install Java 25, OR place a portable Java runtime in:
   runtime\bin\java.exe

2) Download a stable Paper 26.2 server JAR and rename it:
   paper.jar

3) Build VanillaRPG-Core from ../plugin and copy the JAR to:
   plugins\VanillaRPG-Core.jar

4) Start once with start.bat.

5) Read the Minecraft EULA. If you agree, change eula.txt to:
   eula=true

6) Start again, then in console:
   whitelist add <player>
   op <admin>

Portable migration:
- Stop the server cleanly.
- Copy the entire server folder to the new machine.
- Keep plugins/VanillaRPG-Core/rpg.db with the world.
- If using a portable Java runtime, no system Java installation is required.
- Re-open Windows Firewall/router ports on the new machine.

Spawn recommendation:
- Import a community .schem with WorldEdit/FAWE into the normal survival world.
- Keep the hub around 100x100 to 200x200 blocks.
- NPC integration will be added after the RPG core systems are stable.
