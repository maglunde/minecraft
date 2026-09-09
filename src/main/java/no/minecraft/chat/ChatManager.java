package no.minecraft.chat;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.Dimension;
import no.minecraft.world.NetherFortressGenerator;
import no.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class ChatManager {
    public static class ChatMessage {
        private final String text;
        private final float r, g, b;
        private float timeRemaining;

        public ChatMessage(String text, float r, float g, float b) {
            this.text = text;
            this.r = r;
            this.g = g;
            this.b = b;
            this.timeRemaining = 10.0f; // Visible for 10 seconds in HUD
        }

        public String getText() { return text; }
        public float getR() { return r; }
        public float getG() { return g; }
        public float getB() { return b; }
        public float getTimeRemaining() { return timeRemaining; }
        public void update(float dt) { timeRemaining -= dt; }
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private boolean open = false;
    private final StringBuilder inputBuffer = new StringBuilder();

    // Command history
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String savedCurrentInput = "";

    // Autocomplete / Tab completion
    private final List<String> tabCompletions = new ArrayList<>();
    private int tabIndex = -1;
    private String prefixBeforeTab = null;

    // --- Command registry: single source of truth for names, help, execution and completion ---
    @FunctionalInterface
    private interface CommandAction {
        void run(ChatManager chat, String[] args, World world, Player player);
    }

    private static class Command {
        final String[] names;
        final String help;
        final CommandAction action;
        final BiFunction<String[], Boolean, List<String>> completer; // (parts, trailingSpace) -> completions, may be null

        Command(String[] names, String help, CommandAction action,
                BiFunction<String[], Boolean, List<String>> completer) {
            this.names = names;
            this.help = help;
            this.action = action;
            this.completer = completer;
        }
    }

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public ChatManager() {
        registerCommands();
    }

    private void registerCommands() {
        register(new Command(new String[]{"help"}, "/help - Vis kommandoer",
                (chat, args, world, player) -> {
                    chat.addSystemMessage("--- Tilgjengelige kommandoer ---");
                    for (Command c : commands.values()) {
                        chat.addSystemMessage(c.help);
                    }
                }, null));

        register(new Command(new String[]{"give"}, "/give <item> [antall] - Gi deg selv blokker/ting",
                (chat, args, world, player) -> {
                    if (args.length < 2) {
                        chat.addErrorMessage("Bruk: /give <item> [antall]");
                        return;
                    }
                    String itemName = args[1].toUpperCase();
                    int amount = 1;
                    if (args.length >= 3) {
                        try {
                            amount = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            amount = 1;
                        }
                    }
                    amount = Math.clamp(amount, 1, 64);

                    BlockType matched = null;
                    for (BlockType bt : BlockType.values()) {
                        if (bt.name().equalsIgnoreCase(itemName) || bt.getName().equalsIgnoreCase(itemName)) {
                            matched = bt;
                            break;
                        }
                    }
                    if (matched != null && matched != BlockType.AIR) {
                        player.getInventory().addItem(matched, amount);
                        chat.addSuccessMessage("Ga " + amount + "x " + matched.getName() + " til spiller.");
                    } else {
                        chat.addErrorMessage("Ukjent item: " + args[1]);
                    }
                },
                (parts, trailingSpace) -> {
                    List<String> out = new ArrayList<>();
                    String itemPrefix = (parts.length >= 2 && (!trailingSpace || parts.length > 2)) ? parts[1].toLowerCase() : "";
                    if (parts.length == 2 && !trailingSpace) {
                        for (BlockType bt : BlockType.values()) {
                            if (bt == BlockType.AIR) continue;
                            String name = bt.name().toLowerCase();
                            if (name.startsWith(itemPrefix)) {
                                out.add("/give " + name + " ");
                            }
                        }
                    } else if ((parts.length == 2 && trailingSpace) || parts.length == 3) {
                        out.add("/give " + parts[1] + " 64");
                        out.add("/give " + parts[1] + " 16");
                        out.add("/give " + parts[1] + " 1");
                    }
                    return out;
                }));

        register(new Command(new String[]{"tp", "teleport"}, "/tp <x> <y> <z> - Teleporter til koordinater",
                (chat, args, world, player) -> {
                    if (args.length < 4) {
                        chat.addErrorMessage("Bruk: /tp <x> <y> <z>");
                        return;
                    }
                    try {
                        float x = Float.parseFloat(args[1]);
                        float y = Float.parseFloat(args[2]);
                        float z = Float.parseFloat(args[3]);
                        player.teleportTo(x, y, z);
                        chat.addSuccessMessage(String.format("Teleporterte til %.1f, %.1f, %.1f", x, y, z));
                    } catch (NumberFormatException e) {
                        chat.addErrorMessage("Ugyldige koordinater: /tp " + String.join(" ", args).substring(3));
                    }
                }, null));

        register(new Command(new String[]{"locate"}, "/locate <stronghold|fortress|portal> - Finn struktur",
                (chat, args, world, player) -> {
                    if (args.length < 2) {
                        chat.addErrorMessage("Bruk: /locate <stronghold|fortress|portal|end>");
                        return;
                    }
                    String struct = args[1].toLowerCase();
                    if (struct.startsWith("strong")) {
                        chat.addSuccessMessage("Stronghold funnet ved [" + World.STRONGHOLD_X + ", " + World.STRONGHOLD_Y + ", " + World.STRONGHOLD_Z + "]");
                    } else if (struct.startsWith("fort")) {
                        int rX = Math.floorDiv((int) Math.floor(player.getPosition().x / Chunk.SIZE_X), NetherFortressGenerator.FORTRESS_GRID);
                        int rZ = Math.floorDiv((int) Math.floor(player.getPosition().z / Chunk.SIZE_Z), NetherFortressGenerator.FORTRESS_GRID);
                        NetherFortressGenerator.Fortress f = NetherFortressGenerator.getFortressForRegion(rX, rZ, world.getSeed());
                        chat.addSuccessMessage("Nether Fortress funnet ved [" + f.originX + ", 25, " + f.originZ + "] i Nether (2 Blaze spawners)");
                    } else if (struct.startsWith("end") || struct.startsWith("portal")) {
                        chat.addSuccessMessage("End Portal funnet ved [54, 12, 54] i Stronghold");
                    } else {
                        chat.addErrorMessage("Ukjent struktur. Tilgjengelig: stronghold, fortress, end");
                    }
                },
                (parts, trailingSpace) -> {
                    String[] options = {"stronghold", "fortress", "portal", "end"};
                    String locPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    List<String> out = new ArrayList<>();
                    for (String opt : options) {
                        if (opt.startsWith(locPrefix)) {
                            out.add("/locate " + opt);
                        }
                    }
                    return out;
                }));

        register(new Command(new String[]{"gamemode"}, "/gamemode <survival|creative> - Bytt spillmodus",
                (chat, args, world, player) -> {
                    if (args.length < 2) {
                        chat.addErrorMessage("Bruk: /gamemode <survival|creative>");
                        return;
                    }
                    String m = args[1].toLowerCase();
                    if (m.startsWith("c") || m.equals("1")) {
                        player.setGameMode(GameMode.CREATIVE);
                        chat.addSuccessMessage("Spillmodus satt til Kreativ.");
                    } else if (m.startsWith("s") || m.equals("0")) {
                        player.setGameMode(GameMode.SURVIVAL);
                        chat.addSuccessMessage("Spillmodus satt til Overlevelse.");
                    } else {
                        chat.addErrorMessage("Ukjent spillmodus: " + args[1]);
                    }
                },
                (parts, trailingSpace) -> {
                    String[] options = {"survival", "creative"};
                    String gmPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    List<String> out = new ArrayList<>();
                    for (String opt : options) {
                        if (opt.startsWith(gmPrefix)) {
                            out.add("/gamemode " + opt);
                        }
                    }
                    return out;
                }));

        register(new Command(new String[]{"dimension", "dim"}, "/dimension <overworld|nether|end> - Bytt dimensjon",
                (chat, args, world, player) -> {
                    if (args.length < 2) {
                        chat.addErrorMessage("Bruk: /dimension <overworld|nether|end>");
                        return;
                    }
                    String d = args[1].toLowerCase();
                    if (d.startsWith("n")) {
                        world.teleportToDimension(Dimension.NETHER, player);
                        chat.addSuccessMessage("Reiste til Nether.");
                    } else if (d.startsWith("e")) {
                        world.teleportToDimension(Dimension.THE_END, player);
                        chat.addSuccessMessage("Reiste til The End.");
                    } else if (d.startsWith("o")) {
                        world.teleportToDimension(Dimension.OVERWORLD, player);
                        chat.addSuccessMessage("Reiste til Oververden.");
                    } else {
                        chat.addErrorMessage("Ukjent dimensjon: " + args[1]);
                    }
                },
                (parts, trailingSpace) -> {
                    String[] options = {"overworld", "nether", "end"};
                    String dimPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    List<String> out = new ArrayList<>();
                    for (String opt : options) {
                        if (opt.startsWith(dimPrefix)) {
                            out.add("/dimension " + opt);
                        }
                    }
                    return out;
                }));

        register(new Command(new String[]{"heal"}, "/heal - Fyll helse",
                (chat, args, world, player) -> {
                    player.setHealth(Player.MAX_HEALTH);
                    chat.addSuccessMessage("Helse fylt til maksimum!");
                }, null));

        register(new Command(new String[]{"clear"}, "/clear - Tom inventory",
                (chat, args, world, player) -> {
                    player.getInventory().clear();
                    chat.addSuccessMessage("Tømte inventaret.");
                }, null));

        register(new Command(new String[]{"kill"}, "/kill - Drep spiller eller monstre (/kill @e)",
                (chat, args, world, player) -> {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("@e")) {
                        world.getMobs().clear();
                        chat.addSuccessMessage("Fjernet alle monstre.");
                    } else {
                        player.damage(100);
                        chat.addSuccessMessage("Drepte spiller.");
                    }
                },
                (parts, trailingSpace) -> List.of("/kill @e")));

        register(new Command(new String[]{"time", "tid"}, "/time set <day|night|noon|midnight|sunrise|sunset> - Sett tid på døgnet",
                (chat, args, world, player) -> {
                    if (args.length < 2) {
                        chat.addErrorMessage("Bruk: /time set <day|noon|night|midnight|sunrise|sunset|tall>");
                        return;
                    }
                    String arg = args[1].toLowerCase();
                    if (arg.equals("set") || arg.equals("sett")) {
                        if (args.length < 3) {
                            chat.addErrorMessage("Bruk: /time set <day|noon|night|midnight|sunrise|sunset|tall>");
                            return;
                        }
                        arg = args[2].toLowerCase();
                    }

                    switch (arg) {
                        case "day", "dag" -> {
                            world.setTimeOfDay(0.10f);
                            chat.addSuccessMessage("Satte tiden til dag");
                        }
                        case "noon", "midday", "middag" -> {
                            world.setTimeOfDay(0.25f);
                            chat.addSuccessMessage("Satte tiden til middag (kl. 12:00)");
                        }
                        case "sunset", "dusk", "solnedgang", "kveld" -> {
                            world.setTimeOfDay(0.50f);
                            chat.addSuccessMessage("Satte tiden til solnedgang");
                        }
                        case "night", "natt" -> {
                            world.setTimeOfDay(0.65f);
                            chat.addSuccessMessage("Satte tiden til natt");
                        }
                        case "midnight", "midnatt" -> {
                            world.setTimeOfDay(0.75f);
                            chat.addSuccessMessage("Satte tiden til midnatt (kl. 00:00)");
                        }
                        case "sunrise", "dawn", "soloppgang", "morgen" -> {
                            world.setTimeOfDay(0.0f);
                            chat.addSuccessMessage("Satte tiden til soloppgang");
                        }
                        default -> {
                            try {
                                float val = Float.parseFloat(arg);
                                if (val >= 240.0f) {
                                    float frac = (val % 24000.0f) / 24000.0f;
                                    world.setTimeOfDay(frac);
                                    chat.addSuccessMessage(String.format(java.util.Locale.ROOT, "Satte tiden til %.0f ticks (%.1f%% av døgnet)", val, frac * 100.0f));
                                } else {
                                    float frac = (val % World.DAY_LENGTH_SECONDS) / World.DAY_LENGTH_SECONDS;
                                    world.setTimeOfDay(frac);
                                    chat.addSuccessMessage(String.format(java.util.Locale.ROOT, "Satte tiden til %.1f sekunder (%.1f%% av døgnet)", val, frac * 100.0f));
                                }
                            } catch (NumberFormatException e) {
                                chat.addErrorMessage("Ukjent tidsverdi: " + arg + ". Bruk day, noon, night, midnight, sunrise, sunset eller tall.");
                            }
                        }
                    }
                },
                (parts, trailingSpace) -> {
                    String[] subCommands = {"set", "day", "night", "noon", "midnight", "sunrise", "sunset"};
                    String root = parts[0];
                    List<String> out = new ArrayList<>();
                    if (parts.length >= 2 && (parts[1].equalsIgnoreCase("set") || parts[1].equalsIgnoreCase("sett"))) {
                        String[] setOptions = {"day", "night", "noon", "midnight", "sunrise", "sunset"};
                        String optPrefix = (parts.length >= 3 && !trailingSpace) ? parts[2].toLowerCase() : "";
                        for (String opt : setOptions) {
                            if (opt.startsWith(optPrefix)) {
                                out.add(root + " set " + opt);
                            }
                        }
                    } else {
                        String timePrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                        for (String opt : subCommands) {
                            if (opt.startsWith(timePrefix)) {
                                out.add(root + " " + opt);
                            }
                        }
                    }
                    return out;
                }));
    }

    private void register(Command command) {
        for (String name : command.names) {
            commands.put(name, command);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void openChat(String initialText) {
        this.open = true;
        this.inputBuffer.setLength(0);
        if (initialText != null) {
            this.inputBuffer.append(initialText);
        }
        this.historyIndex = -1;
        this.savedCurrentInput = "";
        clearTabState();
    }

    public void closeChat() {
        this.open = false;
        this.inputBuffer.setLength(0);
        this.historyIndex = -1;
        clearTabState();
    }

    public String getInputText() {
        return inputBuffer.toString();
    }

    public void addChar(char c) {
        if (!open) return;
        clearTabState();
        if (inputBuffer.length() < 120) {
            if (c >= 32 && c < 256) {
                inputBuffer.append(c);
            }
        }
    }

    public void backspace() {
        if (!open) return;
        clearTabState();
        if (inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
        }
    }

    private void clearTabState() {
        tabCompletions.clear();
        tabIndex = -1;
        prefixBeforeTab = null;
    }

    public void navigateHistory(int direction) {
        if (!open || history.isEmpty()) return;
        clearTabState();

        if (direction < 0) { // UP arrow: Go further back in history
            if (historyIndex == -1) {
                savedCurrentInput = inputBuffer.toString();
                historyIndex = history.size() - 1;
            } else if (historyIndex > 0) {
                historyIndex--;
            }
            inputBuffer.setLength(0);
            inputBuffer.append(history.get(historyIndex));
        } else if (direction > 0) { // DOWN arrow: Go forward in history
            if (historyIndex != -1) {
                if (historyIndex < history.size() - 1) {
                    historyIndex++;
                    inputBuffer.setLength(0);
                    inputBuffer.append(history.get(historyIndex));
                } else {
                    historyIndex = -1;
                    inputBuffer.setLength(0);
                    inputBuffer.append(savedCurrentInput);
                }
            }
        }
    }

    public void handleTabCompletion() {
        if (!open) return;

        String current = inputBuffer.toString();
        if (tabCompletions.isEmpty()) {
            prefixBeforeTab = current;
            buildCompletions(current);
            if (tabCompletions.isEmpty()) return;
            tabIndex = 0;
        } else {
            tabIndex = (tabIndex + 1) % tabCompletions.size();
        }

        inputBuffer.setLength(0);
        inputBuffer.append(tabCompletions.get(tabIndex));
    }

    private void buildCompletions(String input) {
        tabCompletions.clear();
        String trimmed = input.trim();

        if (trimmed.isEmpty()) {
            return;
        }

        if (trimmed.startsWith("/")) {
            String[] parts = trimmed.split("\\s+");
            boolean trailingSpace = input.endsWith(" ");

            if (parts.length == 1 && !trailingSpace) {
                // Autocomplete root commands from the registry
                String lower = parts[0].toLowerCase();
                for (String name : commands.keySet()) {
                    if (("/" + name).startsWith(lower)) {
                        tabCompletions.add("/" + name + " ");
                    }
                }
            } else {
                String root = parts[0].toLowerCase();
                if (root.startsWith("/")) root = root.substring(1);
                Command c = commands.get(root);
                if (c != null && c.completer != null) {
                    tabCompletions.addAll(c.completer.apply(parts, trailingSpace));
                }
            }
        }
    }

    public void addMessage(String text, float r, float g, float b) {
        messages.add(new ChatMessage(text, r, g, b));
        if (messages.size() > 50) {
            messages.remove(0);
        }
    }

    public void addSystemMessage(String text) {
        addMessage(text, 1.0f, 1.0f, 0.4f);
    }

    public void addErrorMessage(String text) {
        addMessage(text, 1.0f, 0.35f, 0.35f);
    }

    public void addSuccessMessage(String text) {
        addMessage(text, 0.4f, 1.0f, 0.4f);
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void update(float dt) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            messages.get(i).update(dt);
            if (messages.get(i).getTimeRemaining() <= 0) {
                messages.remove(i);
            }
        }
    }

    public void submitMessage(World world, Player player) {
        String raw = inputBuffer.toString().trim();
        closeChat();
        if (raw.isEmpty()) return;

        // Add to history (avoid duplicates at end)
        if (history.isEmpty() || !history.get(history.size() - 1).equals(raw)) {
            history.add(raw);
            if (history.size() > 50) {
                history.remove(0);
            }
        }

        if (raw.startsWith("/")) {
            executeCommand(raw.substring(1).trim(), world, player);
        } else {
            addMessage("<Spiller> " + raw, 1.0f, 1.0f, 1.0f);
        }
    }

    public void executeCommand(String cmdLine, World world, Player player) {
        if (cmdLine.isEmpty()) return;
        String[] parts = cmdLine.split("\\s+");
        String cmd = parts[0].toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        Command command = commands.get(cmd);
        if (command == null) {
            addErrorMessage("Ukjent kommando: /" + cmd + ". Skriv /help for liste over kommandoer.");
            return;
        }
        command.action.run(this, parts, world, player);
    }
}
