package no.minecraft.chat;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.Dimension;
import no.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

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

    // Available root commands
    private static final String[] COMMAND_ROOTS = {
            "/help", "/give", "/tp", "/teleport", "/locate",
            "/gamemode", "/dimension", "/heal", "/clear", "/kill"
    };

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
                // Autocomplete root commands
                String lower = parts[0].toLowerCase();
                for (String cmd : COMMAND_ROOTS) {
                    if (cmd.toLowerCase().startsWith(lower)) {
                        tabCompletions.add(cmd + " ");
                    }
                }
            } else {
                String root = parts[0].toLowerCase();
                if (root.equals("/give")) {
                    String itemPrefix = (parts.length >= 2 && (!trailingSpace || parts.length > 2)) ? parts[1].toLowerCase() : "";
                    if (parts.length == 2 && !trailingSpace) {
                        for (BlockType bt : BlockType.values()) {
                            if (bt == BlockType.AIR) continue;
                            String name = bt.name().toLowerCase();
                            if (name.startsWith(itemPrefix)) {
                                tabCompletions.add("/give " + name + " ");
                            }
                        }
                    } else if ((parts.length == 2 && trailingSpace) || parts.length == 3) {
                        tabCompletions.add("/give " + parts[1] + " 64");
                        tabCompletions.add("/give " + parts[1] + " 16");
                        tabCompletions.add("/give " + parts[1] + " 1");
                    }
                } else if (root.equals("/locate")) {
                    String[] options = {"stronghold", "fortress", "portal", "end"};
                    String locPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    for (String opt : options) {
                        if (opt.startsWith(locPrefix)) {
                            tabCompletions.add("/locate " + opt);
                        }
                    }
                } else if (root.equals("/gamemode")) {
                    String[] options = {"survival", "creative"};
                    String gmPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    for (String opt : options) {
                        if (opt.startsWith(gmPrefix)) {
                            tabCompletions.add("/gamemode " + opt);
                        }
                    }
                } else if (root.equals("/dimension") || root.equals("/dim")) {
                    String[] options = {"overworld", "nether", "end"};
                    String dimPrefix = (parts.length >= 2 && !trailingSpace) ? parts[1].toLowerCase() : "";
                    for (String opt : options) {
                        if (opt.startsWith(dimPrefix)) {
                            tabCompletions.add("/dimension " + opt);
                        }
                    }
                } else if (root.equals("/kill")) {
                    tabCompletions.add("/kill @e");
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

        switch (cmd) {
            case "help" -> {
                addSystemMessage("--- Tilgjengelige kommandoer ---");
                addSystemMessage("/give <item> [antall] - Gi deg selv blokker/ting");
                addSystemMessage("/tp <x> <y> <z> - Teleporter til koordinater");
                addSystemMessage("/locate <stronghold|fortress|portal> - Finn struktur");
                addSystemMessage("/gamemode <survival|creative> - Bytt spillmodus");
                addSystemMessage("/dimension <overworld|nether|end> - Bytt dimensjon");
                addSystemMessage("/clear - Tom inventory");
                addSystemMessage("/heal - Fyll helse");
                addSystemMessage("/kill - Drep spiller eller monstre (/kill @e)");
            }
            case "give" -> {
                if (parts.length < 2) {
                    addErrorMessage("Bruk: /give <item> [antall]");
                    return;
                }
                String itemName = parts[1].toUpperCase();
                int amount = 1;
                if (parts.length >= 3) {
                    try {
                        amount = Integer.parseInt(parts[2]);
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
                    addSuccessMessage("Ga " + amount + "x " + matched.getName() + " til spiller.");
                } else {
                    addErrorMessage("Ukjent item: " + parts[1]);
                }
            }
            case "tp", "teleport" -> {
                if (parts.length < 4) {
                    addErrorMessage("Bruk: /tp <x> <y> <z>");
                    return;
                }
                try {
                    float x = Float.parseFloat(parts[1]);
                    float y = Float.parseFloat(parts[2]);
                    float z = Float.parseFloat(parts[3]);
                    player.teleportTo(x, y, z);
                    addSuccessMessage(String.format("Teleporterte til %.1f, %.1f, %.1f", x, y, z));
                } catch (NumberFormatException e) {
                    addErrorMessage("Ugyldige koordinater: " + cmdLine);
                }
            }
            case "locate" -> {
                if (parts.length < 2) {
                    addErrorMessage("Bruk: /locate <stronghold|fortress|portal|end>");
                    return;
                }
                String struct = parts[1].toLowerCase();
                if (struct.startsWith("strong")) {
                    addSuccessMessage("Stronghold funnet ved [" + World.STRONGHOLD_X + ", " + World.STRONGHOLD_Y + ", " + World.STRONGHOLD_Z + "]");
                } else if (struct.startsWith("fort")) {
                    addSuccessMessage("Nether Fortress funnet ved [0, 15, 0] i Nether");
                } else if (struct.startsWith("end") || struct.startsWith("portal")) {
                    addSuccessMessage("End Portal funnet ved [54, 12, 54] i Stronghold");
                } else {
                    addErrorMessage("Ukjent struktur. Tilgjengelig: stronghold, fortress, end");
                }
            }
            case "gamemode" -> {
                if (parts.length < 2) {
                    addErrorMessage("Bruk: /gamemode <survival|creative>");
                    return;
                }
                String m = parts[1].toLowerCase();
                if (m.startsWith("c") || m.equals("1")) {
                    player.setGameMode(GameMode.CREATIVE);
                    addSuccessMessage("Spillmodus satt til Kreativ.");
                } else if (m.startsWith("s") || m.equals("0")) {
                    player.setGameMode(GameMode.SURVIVAL);
                    addSuccessMessage("Spillmodus satt til Overlevelse.");
                } else {
                    addErrorMessage("Ukjent spillmodus: " + parts[1]);
                }
            }
            case "dimension", "dim" -> {
                if (parts.length < 2) {
                    addErrorMessage("Bruk: /dimension <overworld|nether|end>");
                    return;
                }
                String d = parts[1].toLowerCase();
                if (d.startsWith("n")) {
                    world.teleportToDimension(Dimension.NETHER, player);
                    addSuccessMessage("Reiste til Nether.");
                } else if (d.startsWith("e")) {
                    world.teleportToDimension(Dimension.THE_END, player);
                    addSuccessMessage("Reiste til The End.");
                } else if (d.startsWith("o")) {
                    world.teleportToDimension(Dimension.OVERWORLD, player);
                    addSuccessMessage("Reiste til Oververden.");
                } else {
                    addErrorMessage("Ukjent dimensjon: " + parts[1]);
                }
            }
            case "heal" -> {
                player.setHealth(Player.MAX_HEALTH);
                addSuccessMessage("Helse fylt til maksimum!");
            }
            case "clear" -> {
                player.getInventory().clear();
                addSuccessMessage("Tømte inventaret.");
            }
            case "kill" -> {
                if (parts.length >= 2 && parts[1].equalsIgnoreCase("@e")) {
                    world.getMobs().clear();
                    addSuccessMessage("Fjernet alle monstre.");
                } else {
                    player.damage(100);
                    addSuccessMessage("Drepte spiller.");
                }
            }
            default -> addErrorMessage("Ukjent kommando: /" + cmd + ". Skriv /help for liste over kommandoer.");
        }
    }
}
