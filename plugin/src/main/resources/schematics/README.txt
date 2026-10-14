Poker room schematic
=====================

1. Put your WorldEdit .schem file in this folder.
2. In config.yml, set poker.room.schematic.enabled to true.
3. Set poker.room.schematic.file to the exact file name.
4. Restart the server. The schematic will be pasted in world poker_room.

The schematic origin is pasted at poker.room.anchor.x/y/z. Build the poker
table around that origin:
- player seats: x=-3, 0, 3 and z=-6 or +6 from the anchor
- community cards: x=-2 through +2, y=anchor+3.1, z=anchor+0.5
- player spawn: anchor x, y=anchor+1, z=anchor z

Set ignore-air-blocks: false if the schematic should clear its own interior.
The plugin only pastes a schematic when explicitly enabled; otherwise it uses
the built-in green felt room.
