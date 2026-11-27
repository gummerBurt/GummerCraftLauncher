package com.skcraft.launcher.install;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.LauncherException;
import com.skcraft.launcher.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.io.File;

@RequiredArgsConstructor
@Log
public class FileVerify implements InstallTask {
	private final File target;
	private final String name;
	private final String hash;

	@Override
	public void execute(Launcher launcher) throws Exception {
		log.info("Verifying file " + name);

		// 27.11.2026 (тікети #134, #138): щойно перенесений файл ненадовго тримає антивірус: читання ще двічі з паузами,
		// далі справжня помилка (AccessDeniedException з шляхом), а не «не той sha1»
		String actualHash = null;
		for (int attempt = 0; ; attempt++) {
			try {
				actualHash = FileUtils.getShaHash(target);
				break;
			} catch (java.io.IOException e) {
				if (attempt >= 2) throw e;
				log.info("Cannot read " + target.getAbsolutePath() + " yet (" + e + "), retrying");
				FileMover.pause(attempt == 0 ? 300 : 800);
			}
		}
		if (!actualHash.equals(hash)) {
			String message = String.format(
					"File %s (%s) is corrupt (invalid hash)\n" +
					"Expected '%s'\nGot '%s'",
					name, target.getAbsolutePath(), hash, actualHash);

			throw new LauncherException(message, message);
		}
	}

	@Override
	public double getProgress() {
		return -1;
	}

	@Override
	public String getStatus() {
		return "Verifying " + name;
	}
}
