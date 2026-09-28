import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the mod's JSON resources (blockstates, models, loot tables, recipes, tags, lang).
 *
 * <p>Run from the project root after changing the door list: {@code java tools/GenResources.java}
 * The output is committed, so the mod builds without running this.
 */
public class GenResources {
    static final String MOD = "dynamicdoors";
    static final Path RES = Path.of("src", "main", "resources");

    // id, frame texture, English name, Portuguese name
    static final String[][] WOODS = {
            {"oak", "oak_planks", "Oak", "de Carvalho"},
            {"spruce", "spruce_planks", "Spruce", "de Pinheiro"},
            {"birch", "birch_planks", "Birch", "de Bétula"},
            {"jungle", "jungle_planks", "Jungle", "da Selva"},
            {"acacia", "acacia_planks", "Acacia", "de Acácia"},
            {"dark_oak", "dark_oak_planks", "Dark Oak", "de Carvalho Escuro"},
            {"mangrove", "mangrove_planks", "Mangrove", "de Mangue"},
            {"cherry", "cherry_planks", "Cherry", "de Cerejeira"},
            {"bamboo", "bamboo_planks", "Bamboo", "de Bambu"},
            {"crimson", "crimson_planks", "Crimson", "Carmesim"},
            {"warped", "warped_planks", "Warped", "Distorcida"},
            {"iron", "iron_block", "Iron", "de Ferro"},
    };

    public static void main(String[] args) throws IOException {
        List<String> axe = new ArrayList<>();
        List<String> pickaxe = new ArrayList<>();
        List<String> en = new ArrayList<>();
        List<String> pt = new ArrayList<>();
        for (String[] w : WOODS) {
            String wood = w[0];
            String id = wood + "_round_door";
            String full = MOD + ":" + id;
            boolean iron = wood.equals("iron");
            (iron ? pickaxe : axe).add(full);
            en.add(q("block." + MOD + "." + id) + ": " + q("Round " + w[2] + " Door"));
            pt.add(q("block." + MOD + "." + id) + ": " + q("Porta Redonda " + w[3]));

            write("assets/" + MOD + "/blockstates/" + id + ".json",
                    "{\n  \"variants\": {\n    \"\": { \"model\": \"" + MOD + ":block/" + id + "\" }\n  }\n}\n");
            // Drawn by the block entity; the model only gives the break particles.
            write("assets/" + MOD + "/models/block/" + id + ".json",
                    "{\n  \"textures\": { \"particle\": \"minecraft:block/" + w[1] + "\" }\n}\n");
            write("assets/" + MOD + "/models/item/" + id + ".json", itemModel(wood, w[1]));

            write("data/" + MOD + "/loot_tables/blocks/" + id + ".json", """
                    {
                      "type": "minecraft:block",
                      "pools": [
                        {
                          "rolls": 1,
                          "bonus_rolls": 0,
                          "entries": [ { "type": "minecraft:item", "name": "%s" } ],
                          "conditions": [
                            { "condition": "minecraft:block_state_property", "block": "%s", "properties": { "part": "1" } },
                            { "condition": "minecraft:survives_explosion" }
                          ]
                        }
                      ],
                      "random_sequence": "%s:blocks/%s"
                    }
                    """.formatted(full, full, MOD, id));

            // Wood: 9 planks fill the grid. Iron: 9 ingots is the iron block, so 8 ingots around an iron door.
            String ingredient = iron ? "minecraft:iron_ingot" : "minecraft:" + w[1];
            write("data/" + MOD + "/recipes/" + id + ".json", """
                    {
                      "type": "minecraft:crafting_shaped",
                      "category": "%s",
                      "group": "round_door",
                      "pattern": [ "PPP", "P%sP", "PPP" ],
                      "key": {
                        "P": { "item": "%s" }%s
                      },
                      "result": { "item": "%s" }
                    }
                    """.formatted(iron ? "redstone" : "building", iron ? "D" : "P", ingredient,
                    iron ? ",\n    \"D\": { \"item\": \"minecraft:iron_door\" }" : "", full));

            write("data/" + MOD + "/advancements/recipes/" + (iron ? "redstone" : "building_blocks") + "/" + id + ".json", """
                    {
                      "parent": "minecraft:recipes/root",
                      "criteria": {
                        "has_material": {
                          "trigger": "minecraft:inventory_changed",
                          "conditions": { "items": [ { "items": [ "%s" ] } ] }
                        },
                        "has_the_recipe": {
                          "trigger": "minecraft:recipe_unlocked",
                          "conditions": { "recipe": "%s" }
                        }
                      },
                      "requirements": [ [ "has_material", "has_the_recipe" ] ],
                      "rewards": { "recipes": [ "%s" ] },
                      "sends_telemetry_event": false
                    }
                    """.formatted(ingredient, full, full));
        }
        write("data/minecraft/tags/blocks/mineable/axe.json", tag(axe));
        write("data/minecraft/tags/blocks/mineable/pickaxe.json", tag(pickaxe));
        write("assets/" + MOD + "/lang/en_us.json", "{\n  " + String.join(",\n  ", en) + "\n}\n");
        write("assets/" + MOD + "/lang/pt_br.json", "{\n  " + String.join(",\n  ", pt) + "\n}\n");
    }

    /** A small round panel with its knob, textured like the placed door. */
    static String itemModel(String wood, String frame) {
        List<String> elements = new ArrayList<>();
        int prevX0 = -1, prevX1 = -1, start = 0;
        for (int y = 0; y <= 16; y++) {
            int x0 = -1, x1 = -1;
            if (y < 16) {
                for (int x = 0; x < 16; x++) {
                    double dx = x + 0.5 - 8, dy = y + 0.5 - 8;
                    if (dx * dx + dy * dy <= 7.6 * 7.6) {
                        if (x0 < 0) x0 = x;
                        x1 = x + 1;
                    }
                }
            }
            if (x0 != prevX0 || x1 != prevX1) {
                if (prevX0 >= 0) elements.add(panelRow(prevX0, prevX1, start, y));
                prevX0 = x0;
                prevX1 = x1;
                start = y;
            }
        }
        elements.add("""
                    { "from": [11, 7, 6], "to": [13, 9, 10], "faces": {
                        "north": {"uv": [7, 7, 9, 9], "texture": "#knob"}, "south": {"uv": [7, 7, 9, 9], "texture": "#knob"},
                        "east": {"uv": [7, 7, 9, 9], "texture": "#knob"}, "west": {"uv": [7, 7, 9, 9], "texture": "#knob"},
                        "up": {"uv": [7, 7, 9, 9], "texture": "#knob"}, "down": {"uv": [7, 7, 9, 9], "texture": "#knob"} } }""");
        return """
                {
                  "gui_light": "front",
                  "textures": {
                    "particle": "minecraft:block/%s",
                    "panel": "dynamicdoors:block/%s_round_door",
                    "edge": "minecraft:block/%s",
                    "knob": "dynamicdoors:block/round_door_knob"
                  },
                  "elements": [
                %s
                  ],
                  "display": {
                    "gui": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
                    "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
                    "fixed": { "rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
                    "head": { "rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1] },
                    "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375] },
                    "thirdperson_lefthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375] },
                    "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4] },
                    "firstperson_lefthand": { "rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4] }
                  }
                }
                """.formatted(frame, wood, frame, String.join(",\n", elements));
    }

    static String panelRow(int x0, int x1, int y0, int y1) {
        String face = "[%d, %d, %d, %d]".formatted(x0, 16 - y1, x1, 16 - y0);
        String back = "[%d, %d, %d, %d]".formatted(16 - x1, 16 - y1, 16 - x0, 16 - y0);
        return ("""
                    { "from": [%d, %d, 7], "to": [%d, %d, 9], "faces": {
                        "north": {"uv": %s, "texture": "#panel"}, "south": {"uv": %s, "texture": "#panel"},
                        "east": {"uv": [7, %d, 9, %d], "texture": "#edge"}, "west": {"uv": [7, %d, 9, %d], "texture": "#edge"},
                        "up": {"uv": [%d, 7, %d, 9], "texture": "#edge"}, "down": {"uv": [%d, 7, %d, 9], "texture": "#edge"} } }""")
                .formatted(x0, y0, x1, y1, back, face, 16 - y1, 16 - y0, 16 - y1, 16 - y0, x0, x1, x0, x1);
    }

    static String tag(List<String> values) {
        return "{\n  \"replace\": false,\n  \"values\": [\n" + values.stream().map(v -> "    " + q(v))
                .collect(Collectors.joining(",\n")) + "\n  ]\n}\n";
    }

    static String q(String s) {
        return "\"" + s + "\"";
    }

    static void write(String path, String content) throws IOException {
        Path p = RES.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }
}
