Poker room schematic
=====================

Put a WorldEdit .schem file in this folder, for example poker_room.schem.
Then edit ../config.yml:

poker:
  room:
    schematic:
      enabled: true
      file: poker_room.schem

Restart the server. The schematic will be pasted into world poker_room.

The schematic origin is pasted at poker.room.anchor.x/y/z. Build the poker
table around that origin:
- player seats: x=-3, 0, 3 and z=-6 or +6 from the anchor
- community cards: x=-2 through +2, y=anchor+3.1, z=anchor+0.5
- player spawn: anchor x, y=anchor+1, z=anchor z

Set ignore-air-blocks: false if the schematic should clear its own interior.
