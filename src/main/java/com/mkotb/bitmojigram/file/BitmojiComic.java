package com.mkotb.bitmojigram.file;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

@Getter
public class BitmojiComic {
    @SerializedName("src")
    private String url;
    private int comicId;
    private List<String> tags;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BitmojiComic that = (BitmojiComic) o;

        return comicId == that.comicId;
    }

    @Override
    public int hashCode() {
        return comicId;
    }
}
