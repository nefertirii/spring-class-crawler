package com.example.crawler.coloso;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ColosoCourse {
    private final Long id;
    private final String title;
    private final Long price;
    private final String description;
    private final String keywords;
    private final String instructor;
    private final String mainCategory;
    private final String subCategory;
    private final String url;
    private final String imageUrl;

    @Override
    public String toString() {
        return id + "\t" +
                title + "\t" +
                instructor + "\t" +
                price + "\t" +
                mainCategory + "\t" +
                subCategory + "\t" +
                description + "\t" +
                keywords + "\t" +
                url + "\t" +
                imageUrl;
    }
}
