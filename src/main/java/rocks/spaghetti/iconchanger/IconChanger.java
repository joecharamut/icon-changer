/*
    Copyright (C) 2021 Joseph Charamut

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package rocks.spaghetti.iconchanger;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.ServerMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class IconChanger implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Random random = new Random();
    private static String[] favicons;

    private static File configFile;
    private static boolean sequentialMode = false;
    private static int sequentialCounter = 0;

    @Override
    public void onInitializeServer() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("iconchanger.cfg").toFile();

        LOGGER.info("Hello from IconChanger");

        File iconDir = FabricLoader.getInstance().getGameDir().resolve("icons").toFile();
        if (!iconDir.exists() && !iconDir.mkdir()) {
            LOGGER.error("Error creating icons folder");
            return;
        }

        favicons = Arrays.stream(iconDir.listFiles())
                .filter(file -> file.getName().toLowerCase().endsWith("png"))
                .sorted(File::compareTo)
                .map(IconChanger::readImage)
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        LOGGER.info("Loaded {} icons", favicons.length);

        try {
            Properties props = readConfig();
            sequentialMode = props.getProperty("sequential-mode", "false").equals("true");
        } catch (IOException e) {
            LOGGER.catching(e);
        }

        LOGGER.info("Loaded config");
    }

    public static void getServerMetadataCallback(ServerMetadata metadata) {
        if (favicons.length == 0) return;

        if (sequentialMode) {
            metadata.setFavicon(favicons[sequentialCounter++]);
            if (sequentialCounter >= favicons.length) {
                sequentialCounter = 0;
            }
        } else {
            metadata.setFavicon(favicons[random.nextInt(favicons.length)]);
        }
    }

    private static Properties getDefaultConfig() {
        Properties props = new Properties();
        props.setProperty("sequential-mode", "false");
        return props;
    }

    private static void writeConfig(Properties properties) throws IOException {
        if (!configFile.exists() && !configFile.createNewFile()) {
            LOGGER.error("Error creating config file");
            return;
        }

        try (FileOutputStream os = new FileOutputStream(configFile)) {
            properties.store(os, """
                    IconChanger config
                    
                    sequential-mode: boolean
                    Determines if the icons should be displayed sequentially or in a random order
                    """);
        }
    }

    private static Properties readConfig() throws IOException {
        Properties props = getDefaultConfig();

        try (InputStream is = new FileInputStream(configFile)) {
            props.load(is);
        } catch (FileNotFoundException e) {
            writeConfig(props);
            return props;
        }

        return props;
    }

    private static String readImage(File file) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("Image does not exist");
            }

            BufferedImage image = ImageIO.read(file);

            if (image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IllegalArgumentException("Image must be 64x64");
            }

            ImageIO.write(image, "PNG", os);
            return  "data:image/png;base64," + Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.catching(e);
            return null;
        }
    }
}
