package app.tuxguitar.app.system.icons;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import app.tuxguitar.app.system.config.TGConfigDefaults;
import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.event.TGEventManager;
import app.tuxguitar.ui.appearance.UIAppearance;
import app.tuxguitar.ui.appearance.UIColorAppearance;
import app.tuxguitar.ui.resource.UIColorModel;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.util.properties.TGPropertiesManager;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGSkinManager {

	private static final String DARK_SUFFIX = "-dark";
	private static final double DARK_LUMINANCE_THRESHOLD = 0.5d;

	private TGContext context;
	private String currentSkin;

	private TGSkinManager(TGContext context){
		this.context = context;
		this.loadSkin();
	}

	public void addLoader(TGEventListener listener){
		TGEventManager.getInstance(this.context).addListener(TGSkinEvent.EVENT_TYPE, listener);
	}

	public void removeLoader(TGEventListener listener){
		TGEventManager.getInstance(this.context).removeListener(TGSkinEvent.EVENT_TYPE, listener);
	}

	private void fireChanges(){
		TGEventManager.getInstance(this.context).fireEvent(new TGSkinEvent());
	}

	public void loadSkin() {
		this.currentSkin = this.getCurrentSkin();
	}

	public void reloadSkin() {
		this.loadSkin();

		TGIconManager.getInstance(this.context).onSkinChange();
		TGColorManager.getInstance(this.context).onSkinChange();

		this.fireChanges();
	}

	public boolean shouldReload(){
		return (this.currentSkin == null || !this.currentSkin.equals(this.getCurrentSkin()));
	}

	public String getCurrentSkin() {
		TGConfigManager config = TGConfigManager.getInstance(this.context);
		String configuredSkin = config.getStringValue(TGConfigKeys.SKIN);

		if (!this.isSkinAvailable(configuredSkin)) {
			// Use case: user has upgraded TuxGuitar, and configured skin was deleted in the new version
			// overwrite configured skin: replace by default
			config.setValue(TGConfigKeys.SKIN, TGConfigDefaults.DEFAULT_SKIN);
			configuredSkin = TGConfigDefaults.DEFAULT_SKIN;
		}

		if (config.getBooleanValue(TGConfigKeys.SKIN_AUTO)) {
			String autoSkin = this.resolveAutoSkin(configuredSkin);
			if (this.isSkinAvailable(autoSkin)) {
				return autoSkin;
			}
		}

		return configuredSkin;
	}

	public TGProperties getCurrentSkinInfo() {
		return this.getSkinInfo(this.getCurrentSkin());
	}

	public TGProperties getSkinInfo(String skin) {
		TGPropertiesManager propertiesManager =  TGPropertiesManager.getInstance(this.context);
		TGProperties properties = propertiesManager.createProperties();
		propertiesManager.readProperties(properties, TGSkinInfoHandler.RESOURCE, skin);

		return properties;
	}

	public boolean isSystemDark() {
		Boolean appearanceDark = detectDarkFromAppearance();
		if (Boolean.TRUE.equals(appearanceDark)) {
			return true;
		}
		Boolean kdeDark = detectDarkFromKdeConfig();
		if (kdeDark != null) {
			return kdeDark.booleanValue();
		}
		Boolean gtkThemeDark = detectDarkFromGtkTheme();
		if (gtkThemeDark != null) {
			return gtkThemeDark.booleanValue();
		}
		return (appearanceDark != null && appearanceDark.booleanValue());
	}

	private Boolean detectDarkFromAppearance() {
		try {
			UIAppearance appearance = TGApplication.getInstance(this.context).getAppearance();
			UIColorModel background = appearance.getColorModel(UIColorAppearance.WidgetBackground);
			return Boolean.valueOf(isDarkColor(background));
		} catch (Exception exception) {
			return null;
		}
	}

	private Boolean detectDarkFromGtkTheme() {
		String gtkTheme = getEnvValue("GTK_THEME");
		return isDarkName(gtkTheme);
	}

	private Boolean detectDarkFromKdeConfig() {
		if (!isKdeSession()) {
			return null;
		}
		String kdeSchemeEnv = getEnvValue("KDE_COLOR_SCHEME");
		Boolean envResult = isDarkName(kdeSchemeEnv);
		if (envResult != null) {
			return envResult;
		}

		Path path = Paths.get(System.getProperty("user.home", "."), ".config", "kdeglobals");
		if (!Files.isRegularFile(path)) {
			return null;
		}

		String colorScheme = null;
		boolean inGeneral = false;
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
					continue;
				}
				if (line.startsWith("[") && line.endsWith("]")) {
					inGeneral = "[General]".equals(line);
					continue;
				}
				if (inGeneral && line.startsWith("ColorScheme=")) {
					colorScheme = line.substring("ColorScheme=".length()).trim();
					break;
				}
			}
		} catch (IOException exception) {
			return null;
		}

		return isDarkName(colorScheme);
	}

	private static boolean isKdeSession() {
		String desktop = getEnvValue("XDG_CURRENT_DESKTOP");
		if (desktop != null && desktop.toLowerCase().contains("kde")) {
			return true;
		}
		return (getEnvValue("KDE_FULL_SESSION") != null || getEnvValue("KDE_SESSION_VERSION") != null);
	}

	private static Boolean isDarkName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return null;
		}
		String lower = name.toLowerCase();
		if (lower.contains("dark")) {
			return Boolean.TRUE;
		}
		if (lower.contains("light")) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static String getEnvValue(String key) {
		String value = System.getenv(key);
		return (value == null || value.trim().isEmpty()) ? null : value.trim();
	}

	private boolean isSkinAvailable(String skin) {
		if (skin == null) {
			return false;
		}
		TGProperties skinInfo = getSkinInfo(skin);
		return skinInfo.getValue("name") != null;
	}

	private String resolveAutoSkin(String configuredSkin) {
		if (configuredSkin == null) {
			return null;
		}
		boolean isDark = this.isSystemDark();
		if (isDark) {
			if (configuredSkin.endsWith(DARK_SUFFIX)) {
				return configuredSkin;
			}
			String darkSkin = configuredSkin + DARK_SUFFIX;
			return this.isSkinAvailable(darkSkin) ? darkSkin : configuredSkin;
		}

		if (configuredSkin.endsWith(DARK_SUFFIX)) {
			String lightSkin = configuredSkin.substring(0, configuredSkin.length() - DARK_SUFFIX.length());
			return this.isSkinAvailable(lightSkin) ? lightSkin : configuredSkin;
		}

		return configuredSkin;
	}

	private static boolean isDarkColor(UIColorModel color) {
		if (color == null) {
			return false;
		}
		double luminance = ((0.2126d * color.getRed()) + (0.7152d * color.getGreen()) + (0.0722d * color.getBlue())) / 255d;
		return (luminance < DARK_LUMINANCE_THRESHOLD);
	}

	public TGProperties getCurrentSkinProperties() {
		return this.getSkinProperties(this.getCurrentSkin());
	}

	public TGProperties getSkinProperties(String skin) {
		TGPropertiesManager propertiesManager =  TGPropertiesManager.getInstance(this.context);
		TGProperties properties = propertiesManager.createProperties();
		propertiesManager.readProperties(properties, TGSkinPropertiesHandler.RESOURCE, skin);

		return properties;
	}

	public void dispose() {
		TGIconManager.getInstance(this.context).onSkinDisposed();
		TGColorManager.getInstance(this.context).onSkinDisposed();
	}

	public static TGSkinManager getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGSkinManager.class.getName(), new TGSingletonFactory<TGSkinManager>() {
			public TGSkinManager createInstance(TGContext context) {
				return new TGSkinManager(context);
			}
		});
	}
}
