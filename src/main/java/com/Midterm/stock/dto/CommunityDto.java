package com.Midterm.stock.dto;

import java.sql.Timestamp;

public class CommunityDto {
    private int board_id;
    private int user_num;
    private String userName;  // detail.html 에서 사용

    private String category;
    private String title;
    private String content;
    private int view_count;
    private int like_count;
    private Timestamp created_at;
    private Timestamp updated_at;

    public CommunityDto() {

    }

    public CommunityDto(int board_id, int user_num, String category, String title, String content, int view_count, int like_count, Timestamp created_at, Timestamp updated_at) {
        this.board_id = board_id;
        this.user_num = user_num;
        this.category = category;
        this.title = title;
        this.content = content;
        this.view_count = view_count;
        this.like_count = like_count;
        this.created_at = created_at;
        this.updated_at = updated_at;
    }

    public int getBoard_id() {
        return board_id;
    }

    public void setBoard_id(int board_id) {
        this.board_id = board_id;
    }

    public int getUser_num() {
        return user_num;
    }

    public void setUser_num(int user_num) {
        this.user_num = user_num;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getView_count() {
        return view_count;
    }

    public void setView_count(int view_count) {
        this.view_count = view_count;
    }

    public int getLike_count() {
        return like_count;
    }

    public void setLike_count(int like_count) {
        this.like_count = like_count;
    }

    public Timestamp getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public Timestamp getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }
}
