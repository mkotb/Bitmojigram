package com.mkotb.bitmojigram.file;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class DataFile {
    private Map<Long, UUID> bitmojiIdMap = new HashMap<>();
}
