package com.mkotb.bitmojigram;

import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.chat.id.ChatId;
import com.jtelegram.api.commands.Command;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.ex.TelegramException;
import com.jtelegram.api.inline.InlineQuery;
import com.jtelegram.api.inline.input.InputTextMessageContent;
import com.jtelegram.api.inline.keyboard.InlineKeyboardButton;
import com.jtelegram.api.inline.keyboard.InlineKeyboardMarkup;
import com.jtelegram.api.inline.keyboard.InlineKeyboardRow;
import com.jtelegram.api.inline.result.InlineResultArticle;
import com.jtelegram.api.inline.result.InlineResultPhoto;
import com.jtelegram.api.requests.inline.AnswerInlineQuery;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.update.PollingUpdateProvider;
import com.jtelegram.api.util.TextBuilder;
import com.mkotb.bitmojigram.bitmoji.AvatarResponse;
import com.mkotb.bitmojigram.bitmoji.LoginResponse;
import com.mkotb.bitmojigram.file.BitmojiComic;
import com.mkotb.bitmojigram.file.BitmojiComicFile;
import com.mkotb.bitmojigram.file.DataFile;
import lombok.Getter;
import okhttp3.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class BitmojigramBot {
    private static final String FORM_DATA_TEMPLATE = "client_id=imoji&grant_type=password&client_secret=secret&username=%s&password=%s";
    @Getter
    private static BitmojigramBot instance;
    BitmojiComicFile comicFile;
    Map<String, List<BitmojiComic>> searchResults = new HashMap<>();
    private DataFile dataFile;
    private TelegramBot bot;
    private Timer timer;

    public BitmojigramBot(String[] args) throws IOException {
        instance = this;
        timer = new Timer();

        this.loadDataFile();

        TelegramBotRegistry.builder()
                .updateProvider(new PollingUpdateProvider())
                .build()
                .registerBot(args[0], (bot, error) -> {
                    if (error != null) {
                        System.out.println("Unable to start bot! Shutting down..");
                        error.printStackTrace();

                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ignored) {
                            return;
                        }

                        System.exit(-1);
                        return;
                    }

                    System.out.println("Logged in as @" + bot.getBotInfo().getUsername());
                    this.bot = bot;

                    timer.scheduleAtFixedRate(new ComicFileTask(), 0, TimeUnit.HOURS.toMillis(1));

                    bot.getEventRegistry().registerEvent(InlineQueryEvent.class, (e) -> {
                        InlineQuery query = e.getQuery();

                        // have they linked their bitmoji?
                        if (!dataFile.getBitmojiIdMap().containsKey(query.getFrom().getId())) {
                            bot.perform(AnswerInlineQuery.builder()
                                    .queryId(query.getId())
                                    .switchPmText("Link your Bitmoji")
                                    .switchPmParameter("_")
                                    .isPersonal(true)
                                    .cacheTime(0)
                                    .build());
                            return;
                        }

                        // if we haven't loaded our comics yet, don't return anything
                        if (comicFile == null) {
                            return;
                        }

                        UUID id = dataFile.getBitmojiIdMap().get(query.getFrom().getId());
                        AnswerInlineQuery.AnswerInlineQueryBuilder builder = AnswerInlineQuery.builder()
                                .queryId(query.getId())
                                .isPersonal(true)
                                // results can remain cached for up to 15 minutes
                                .cacheTime((int) TimeUnit.MINUTES.toSeconds(15));
                        Set<BitmojiComic> comics = new HashSet<>();
                        List<String> terms = new ArrayList<>(searchResults.keySet());
                        int index = 0;
                        String queryString = query.getQuery();

                        if (!queryString.isEmpty()) {
                            terms.removeIf((s) -> !s.contains(queryString));
                            terms.sort(Comparator.comparingInt((s) -> stringDistance(queryString, s)));
                        }

                        // add the comics up until you have 50 comics
                        while (comics.size() < 49 && index + 1 < terms.size()) {
                            comics.addAll(searchResults.get(terms.get(index)));
                            index++;
                        }

                        if (comics.size() > 50) {
                            comics = comics.stream().limit(50).collect(Collectors.toSet());
                        }

                        // add each result as a photo
                        comics.forEach((comic) -> {
                            String url = String.format(comic.getUrl(), id.toString());

                            builder.addResult(InlineResultPhoto.builder()
                                    .id(String.valueOf(comic.getComicId()))
                                    .url(url)
                                    .thumbUrl(url)
                                    .build());
                        });

                        bot.perform(builder.errorHandler(TelegramException::printStackTrace).build());
                    });

                    bot.getCommandRegistry().registerCommand("start", (e, command) -> {
                        bot.perform(SendText.builder()
                                .chatId(ChatId.of(e.getMessage()))
                                .text(TextBuilder.create()
                                        .plain("Welcome to Bitmojigram! You can link your bitmoji to this bot using the ")
                                        .link("following tutorial", "https://github.com/mkotb/Bitmojigram/wiki/Setup")
                                )
                                .build()
                        );
                        return false;
                    });

                    bot.getCommandRegistry().registerCommand("login", (e, command) -> {
                        if (command.getArgs().size() >= 2) {
                            bot.perform(SendText.builder()
                                    .chatId(ChatId.of(e.getMessage()))
                                    .text("Please wait as the bot attempts to login...")
                                    .build());

                            UUID id = login(command);

                            if (id == null) {
                                bot.perform(SendText.builder()
                                        .chatId(ChatId.of(e.getMessage()))
                                        .text("The bot could not login! Please ensure you have entered your credentials correctly and try again later")
                                        .errorHandler(TelegramException::printStackTrace)
                                        .build());
                                return false;
                            }

                            dataFile.getBitmojiIdMap().put(command.getSender().getId(), id);
                            this.saveDataFile();

                            bot.perform(SendText.builder()
                                    .chatId(ChatId.of(e.getMessage()))
                                    .text("Your bitmoji is successfully linked! Use @BitmojigramBot inline to send bitmoji!")
                                    .errorHandler(TelegramException::printStackTrace)
                                    .build());
                            return false;
                        }

                        bot.perform(SendText.builder()
                                .chatId(ChatId.of(e.getMessage()))
                                .text("Please provide 2 arguments. First one being your email and password for your Bitmoji account")
                                .build());
                        return false;
                    });

                    bot.getCommandRegistry().registerCommand("register", (e, command) -> {
                        if (command.getArgs().size() >= 1) {
                            UUID id;

                            try {
                                id = UUID.fromString(command.getArgs().get(0));
                            } catch (IllegalArgumentException ex) {
                                bot.perform(SendText.builder()
                                        .chatId(ChatId.of(e.getMessage().getChat()))
                                        .text("Illegal id!")
                                        .build());
                                return false;
                            }

                            dataFile.getBitmojiIdMap().put(command.getSender().getId(), id);
                            this.saveDataFile();

                            bot.perform(SendText.builder()
                                    .chatId(ChatId.of(e.getMessage().getChat()))
                                    .text("Successfully registered! Inline functionality should work with the specified UUID now!")
                                    .build());
                        }

                        return false;
                    });
                });
    }

    public void loadDataFile() throws IOException {
        File file = new File("data.json");

        if (!file.exists()) {
            file.createNewFile();
            dataFile = new DataFile();
            this.saveDataFile();
            return;
        }

        dataFile = TelegramBotRegistry.GSON.fromJson(new FileReader(file), DataFile.class);
    }

    public boolean saveDataFile() {
        try {
            Files.write(Paths.get("data.json"), Collections.singleton(TelegramBotRegistry.GSON.toJson(dataFile)));
            return true;
        } catch (IOException ex) {
            System.out.println("Could not save data file!!");
            ex.printStackTrace();
            return false;
        }
    }

    public UUID login(Command command) {
        String email = URLEncoder.encode(command.getArgs().get(0));
        String password = URLEncoder.encode(command.getArgs().get(1));

        // bitmoji doesn't have oauth so we have to just login manually to get their
        // bitmoji's UUID

        OkHttpClient client = bot.getRegistry().getClient();
        Request request = new Request.Builder()
                .url("https://api.bitmoji.com/user/login")
                .post(FormBody.create(MediaType.parse("application/x-www-form-urlencoded"), String.format(FORM_DATA_TEMPLATE, email, password)))
                .build();

        try {
            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();

            if (body == null) {
                return null;
            }

            LoginResponse loginResponse = TelegramBotRegistry.GSON.fromJson(body.string(), LoginResponse.class);

            request = new Request.Builder()
                    .url("https://api.bitmoji.com/user/avatar")
                    .header("bitmoji-token", loginResponse.getAccessToken())
                    .build();

            response = client.newCall(request).execute();
            body = response.body();

            if (body == null) {
                return null;
            }

            return TelegramBotRegistry.GSON.fromJson(body.string(), AvatarResponse.class).getAvatarVersionId();
        } catch (Exception ex) {
            System.out.println("Couldn't login due to unexpected exception!");
            ex.printStackTrace();
            return null;
        }
    }


    public int stringDistance(String left, String right) {
        int len0 = left.length() + 1;
        int len1 = right.length() + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newCost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++) cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {
            // initial cost of skipping prefix in String s1
            newCost[0] = j;

            // transformation cost for each letter in s0
            for(int i = 1; i < len0; i++) {
                // matching current letters in both strings
                int match = (left.charAt(i - 1) == right.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int costReplace = cost[i - 1] + match;
                int costInsert  = cost[i] + 1;
                int costDelete  = newCost[i - 1] + 1;

                // keep minimum cost
                newCost[i] = Math.min(Math.min(costInsert, costDelete), costReplace);
            }

            // swap cost/newCost arrays
            int[] swap = cost; cost = newCost; newCost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }
}
