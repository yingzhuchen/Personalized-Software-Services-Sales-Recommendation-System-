package com.example.jobrec.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {
    public static final String SOURCE_INNOVA_CATALOG = "innova_catalog";
    public static final String SOURCE_MARKET = "market";

    private String id;
    private String title;
    private String seller;
    private String price;
    private String source;
    private String sourceType;
    private String description;
    private List<String> features;
    private String url;
    private Set<String> keywords;
    private boolean favorite;

    public Item() {
    }

    public Item(String id, String title, String seller, String price, String source,
                String description, List<String> features, String url,
                Set<String> keywords, boolean favorite) {
        this(id, title, seller, price, source, SOURCE_MARKET, description, features, url, keywords, favorite);
    }

    public Item(String id, String title, String seller, String price, String source, String sourceType,
                String description, List<String> features, String url,
                Set<String> keywords, boolean favorite) {
        this.id = id;
        this.title = title;
        this.seller = seller;
        this.price = price;
        this.source = source;
        this.sourceType = sourceType;
        this.description = description;
        this.features = features;
        this.url = url;
        this.keywords = keywords;
        this.favorite = favorite;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("seller")
    public String getSeller() {
        return seller;
    }

    @JsonProperty("price")
    public String getPrice() {
        return price;
    }

    @JsonProperty("source")
    public String getSource() {
        return source;
    }

    @JsonProperty("source_type")
    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("features")
    public List<String> getFeatures() {
        return features;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }

    public boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    /** Stored in the address column of the items table. */
    public String getLocation() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Item item = (Item) o;
        return favorite == item.favorite
                && Objects.equals(id, item.id)
                && Objects.equals(title, item.title)
                && Objects.equals(seller, item.seller)
                && Objects.equals(price, item.price)
                && Objects.equals(source, item.source)
                && Objects.equals(sourceType, item.sourceType)
                && Objects.equals(description, item.description)
                && Objects.equals(features, item.features)
                && Objects.equals(url, item.url)
                && Objects.equals(keywords, item.keywords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, seller, price, source, sourceType, description, features, url, keywords, favorite);
    }
}
