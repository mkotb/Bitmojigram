package com.mkotb.bitmojigram;

import com.jtelegram.api.TelegramBotRegistry;
import com.mkotb.bitmojigram.file.BitmojiComic;
import com.mkotb.bitmojigram.file.BitmojiComicFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ComicFileTask extends TimerTask {
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss-SSSXXX");

    @Override
    public void run() {
        OkHttpClient client = BitmojigramBot.getInstance().getBot().getRegistry().getClient();
        Request request = new Request.Builder()
                .url("https://api.bitmoji.com/bitmoji-web?useuuid=true&time=" + timeFormat.format(new Date(System.currentTimeMillis())).replace(":", "%3A"))
                .build();

        try {
            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();

            if (response.code() != 200 || body == null) {
                System.out.println("Oh no! Bitmoji's API returned a non-200 error code");

                if (body != null) {
                    System.out.println("Body: " + body.string());
                }
                return;
            }

            BitmojigramBot.getInstance().comicFile = TelegramBotRegistry.GSON.fromJson(body.string(), BitmojiComicFile.class);
            System.out.println("Successfully refreshed our comic file!");

            BitmojiComicFile comicFile = BitmojigramBot.getInstance().comicFile;
            Map<String, List<BitmojiComic>> searchResults = new HashMap<>();

            comicFile.getComics().forEach((comic) -> {
                comic.getTags().forEach((tag) -> {
                    searchResults.putIfAbsent(tag, new ArrayList<>());
                    searchResults.get(tag).add(comic);
                });
            });

            System.out.println("There are " + searchResults.keySet().size() + " search terms");

            BitmojigramBot.getInstance().searchResults = searchResults;
        } catch (IOException ex) {
            System.out.println("Could not fetch comic file!");
            ex.printStackTrace();
            return;
        }
    }
}
