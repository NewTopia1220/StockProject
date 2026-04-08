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

    // 파싱 필드
    private String companyName;
    private String sectorName;

    // 좋아요/댓글 (서버사이드 초기값)
    private int likeCount;
    private int commentCount;
    private boolean liked;

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
    public void setClickbaitProb(double v) { this.clickbaitProb = v; }

    public String getArticleType() { return articleType; }
    public void setArticleType(String articleType) { this.articleType = articleType; }

    public double getTypeProb() { return typeProb; }
    public void setTypeProb(double v) { this.typeProb = v; }

    public String getCompanyName() { return companyName; }
    public String getSectorName()  { return sectorName;  }

    public int  getLikeCount()    { return likeCount; }
    public void setLikeCount(int v) { this.likeCount = v; }

    public int  getCommentCount()    { return commentCount; }
    public void setCommentCount(int v) { this.commentCount = v; }

    public boolean isLiked()       { return liked; }
    public void setLiked(boolean v) { this.liked = v; }

    private void parseCategory(String category) {
        if (category == null || category.isEmpty()) {
            this.companyName = category; this.sectorName = ""; return;
        }
        int openIdx  = category.lastIndexOf('(');
        int closeIdx = category.lastIndexOf(')');
        if (openIdx > 0 && closeIdx > openIdx) {
            this.companyName = category.substring(0, openIdx).trim();
            this.sectorName  = category.substring(openIdx + 1, closeIdx).trim();
        } else {
            this.companyName = category.trim();
            this.sectorName  = "";
        }
    }

    public String getClickbaitProbStr() { return String.format("%.1f", clickbaitProb); }
    public String getTypeProbStr()      { return String.format("%.1f", typeProb); }
}
