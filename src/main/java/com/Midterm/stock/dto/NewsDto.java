package com.Midterm.stock.dto;

public class NewsDto {
    private String link;
    private String category;
    private String title;
    private String summary;
    private String sentiment;
    private String pubDate;
    private double clickbaitProb;
    private String articleType;
    private double typeProb;

    // category에서 파싱
    private String companyName;
    private String sectorName;

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        this.category = category;
        parseCategory(category);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public String getPubDate() { return pubDate; }
    public void setPubDate(String pubDate) { this.pubDate = pubDate; }

    public double getClickbaitProb() { return clickbaitProb; }
    public void setClickbaitProb(double clickbaitProb) { this.clickbaitProb = clickbaitProb; }

    public String getArticleType() { return articleType; }
    public void setArticleType(String articleType) { this.articleType = articleType; }

    public double getTypeProb() { return typeProb; }
    public void setTypeProb(double typeProb) { this.typeProb = typeProb; }

    public String getCompanyName() { return companyName; }
    public String getSectorName() { return sectorName; }

    // "네이버 (IT/플랫폼)" → companyName="네이버", sectorName="IT/플랫폼"
    private void parseCategory(String category) {
        if (category == null || category.isEmpty()) {
            this.companyName = category;
            this.sectorName = "";
            return;
        }
        int openIdx = category.lastIndexOf('(');
        int closeIdx = category.lastIndexOf(')');
        if (openIdx > 0 && closeIdx > openIdx) {
            this.companyName = category.substring(0, openIdx).trim();
            this.sectorName = category.substring(openIdx + 1, closeIdx).trim();
        } else {
            this.companyName = category.trim();
            this.sectorName = "";
        }
    }

    // 소수점 1자리 퍼센트 표시용 헬퍼
    public String getClickbaitProbStr() {
        return String.format("%.1f", clickbaitProb);
    }
    public String getTypeProbStr() {
        return String.format("%.1f", typeProb);
    }
}
