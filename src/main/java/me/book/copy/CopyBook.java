package me.book.copy;

import me.book.copy.commands.CopyBookCommand;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import org.slf4j.Logger;

public class CopyBook extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Copy Book");

        Commands.add(new CopyBookCommand());
    }

    @Override
    public String getPackage() {
        return "me.book.copy";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MrAngalo", "copy-book");
    }
}
