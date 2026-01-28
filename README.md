# SMCBlacksmith

**SMCBlacksmith** is a custom Minecraft blacksmithing plugin.  
This is the file structure of SMCBlacksmith, showing each core file, its purpose, and where it fits in the plugin.

**Main Plugin / Core**

- **SMCBlacksmith.java** – The main plugin class that starts everything.  
- **plugin.yml** – Registers the plugin on the server.  
- **ConfigManager.java** – Handles loading and saving all configuration files.  
- **MainConfig.java** – Holds values from `config.yml`.  
- **MessageConfig.java** – Loads language settings and in-game messages.  

**Furnace System**

- **FurnaceConfig.java** – Loads all furnace types from `furnaces.yml`.  
- **FurnaceType.java** – Stores information about each furnace type.  
- **FurnaceRecipe.java** – Contains details for each smelting recipe.  
- **FuelConfig.java** – Loads fuel information from `fuels.yml`.  
- **FurnaceManager.java** – Keeps track of all furnaces in the world.  
- **FurnaceInstance.java** – Represents a single placed furnace and its current state.  
- **FurnaceGUI.java** – The GUI players use to interact with furnaces.  
- **FurnaceListener.java** – Handles clicks and events inside the furnace GUI.  
- **TemperatureBar.java** – Displays the furnace’s temperature visually.  
- **TaskManager.java** – Manages ticking and scheduled tasks for furnaces.  

**Forge Minigame**

- **BlacksmithConfig.java** – Loads miscellaneous blacksmith settings.  
- **ForgeConfig.java** – Contains forge minigame recipes.  
- **ForgeRecipe.java** – Holds information about a single forge recipe.  
- **ForgeManager.java** – Handles forge minigame sessions and logic.  
- **ForgeSession.java** – Tracks a player’s in-progress minigame session.  
- **ForgeMinigameGUI.java** – The interface for the forge minigame.  

**Grindstone & Repair**

- **GrindstoneConfig.java** – Loads repair-related settings.  
- **RepairConfigData.java** – Defines each repair option available.  
- **RepairManager.java** – Manages item repair logic.  
- **GrindstoneListener.java** – Allows players to repair items using the grindstone.  

**Item Systems**

- **ItemProvider.java** – Interface for handling items across different systems.  
- **ItemProviderRegistry.java** – Registers item systems like `minecraft`, `nexo`, `smccore`, and `craftengine`.  

**Integrations**

- **SMCCoreHook.java** – Adds support for SMCCore items.  
- **NexoHook.java** – Adds support for Nexo items.  
- **CraftEngineHook.java** – Adds support for CraftEngine items.  
- **PlaceholderAPIHook.java** – Optional integration with PlaceholderAPI.  

**Commands & Utilities**

- **BlacksmithCommand.java** – Handles all `/blacksmith` commands.  
- **ColorUtil.java** – Utility for coloring chat and GUI text.  

**Config Files**

- **config.yml** – Main plugin settings.  
- **furnaces.yml** – All furnace types and recipes.  
- **fuels.yml** – List of valid fuels for furnaces.  
- **blacksmith.yml** – Forge minigame recipes.  
- **grindstone.yml** – Repair and upgrade settings.  
- **lang/en_US.yml** – English text and messages.
