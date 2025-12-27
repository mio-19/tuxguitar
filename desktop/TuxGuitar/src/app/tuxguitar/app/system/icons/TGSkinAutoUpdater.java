package app.tuxguitar.app.system.icons;

import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGSkinAutoUpdater {

	private static final long POLL_INTERVAL_MS = 2000L;

	private TGContext context;
	private Thread thread;
	private boolean running;
	private Boolean lastDarkMode;

	private TGSkinAutoUpdater(TGContext context) {
		this.context = context;
	}

	public void start() {
		if (this.thread != null) {
			return;
		}
		this.running = true;
		this.thread = new Thread(new Runnable() {
			public void run() {
				runLoop();
			}
		}, "tg-skin-auto");
		this.thread.setDaemon(true);
		this.thread.start();
		this.runCheck();
	}

	public void dispose() {
		this.running = false;
		if (this.thread != null) {
			this.thread.interrupt();
			this.thread = null;
		}
	}

	private void runLoop() {
		while (this.running) {
			try {
				Thread.sleep(POLL_INTERVAL_MS);
			} catch (InterruptedException exception) {
				break;
			}
			if (!this.running || TGApplication.getInstance(this.context).isDisposed()) {
				break;
			}
			this.runCheck();
		}
	}

	private void runCheck() {
		TGApplication.getInstance(this.context).getApplication().runInUiThread(new Runnable() {
			public void run() {
				checkNow();
			}
		});
	}

	private void checkNow() {
		if (!this.running || TGApplication.getInstance(this.context).isDisposed()) {
			this.running = false;
			return;
		}
		TGConfigManager config = TGConfigManager.getInstance(this.context);
		if (!config.getBooleanValue(TGConfigKeys.SKIN_AUTO)) {
			this.lastDarkMode = null;
			return;
		}
		TGSkinManager skinManager = TGSkinManager.getInstance(this.context);
		boolean darkMode = skinManager.isSystemDark();
		if (this.lastDarkMode == null || this.lastDarkMode.booleanValue() != darkMode) {
			this.lastDarkMode = darkMode;
			if (skinManager.shouldReload()) {
				skinManager.reloadSkin();
			}
		}
	}

	public static TGSkinAutoUpdater getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGSkinAutoUpdater.class.getName(), new TGSingletonFactory<TGSkinAutoUpdater>() {
			public TGSkinAutoUpdater createInstance(TGContext context) {
				return new TGSkinAutoUpdater(context);
			}
		});
	}
}
