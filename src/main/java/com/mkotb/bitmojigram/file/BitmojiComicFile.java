package com.mkotb.bitmojigram.file;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

@Getter
public class BitmojiComicFile {
    @SerializedName("imoji")
    private List<BitmojiComic> comics;
}
