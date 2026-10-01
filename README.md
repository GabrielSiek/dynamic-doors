# Dynamic Doors

Forge 1.20.1 (47.3.0) mod.

## Round doors

A 3x3 hobbit-style door for every vanilla door: oak, spruce, birch, jungle, acacia, dark oak,
mangrove, cherry, bamboo, crimson, warped and iron. A square planks frame with a round opening; a round
panel sits behind it and swings away from the side you placed it from. Each panel is one picture of
full-height boards in that wood's colours and style (drawn by `tools/GenTextures.java`), with one knob
on the side that opens.

- Recipe: 9 planks of the wood filling the crafting grid. Iron: 8 iron ingots around 1 iron door
  (9 ingots is already the iron block).
- Place it on the ground: the block you aim at becomes the bottom middle, and it needs a free 3x3 space.
  Aim left of centre for a left hinge and right of centre for a right hinge.
- Right-click any part of it to open or close it, including the swung-open panel. Redstone next to any
  of its blocks also works. The iron door opens with redstone only.
- Breaking any part breaks the whole door and drops one item.
- When open, the panel is solid. The 9 spots it swings into (behind the hinge side) get invisible
  parts that follow the round panel's shape. You can't walk through them, and clicking or breaking
  them acts on the door. A spot that already holds a block or water is skipped, not overwritten.

## Animations

Doors, trapdoors and fence gates swing open and closed. This covers vanilla blocks and any modded
block that extends the vanilla classes. It is client-side only and uses no mixins. Set it up in
`config/dynamicdoors-client.toml`: turn each kind on or off, and set `durationTicks` (default 7) and
`roundDoorDurationTicks` (default 10).

Blocks that animate themselves are left alone. That means any door, trapdoor or gate with its own
block entity (such as Create's sliding doors), plus every block from the mods in `excludedMods`
(default `["create"]`). After editing that list, reload resources with F3+T.

## Development

Gradle 8.8 needs JDK 17 (it fails on Java 25):

```
JAVA_HOME=C:/Users/User/.jdks/jdk-17.0.20.1+1 ./gradlew build              # jar in build/libs
JAVA_HOME=... ./gradlew runGameTestServer                                   # multiblock GameTests
DYNAMICDOORS_WORLD=<save> JAVA_HOME=... ./gradlew runClient                   # open a world directly
```

After changing the door list, regenerate the JSON resources with `java tools/GenResources.java`.
