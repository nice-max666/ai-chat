package com.example.ai_chat.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Data // Lombok：自动生成 get/set 方法
@Entity // 告诉 JPA 这是一个数据库表映射类
@Table(name = "assistants") // 对应你数据库里的表名
public class Assistant {

    @Id
    private String id;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private String userId;

    private String personality;

    private String description;

    // 【优化】：直接声明为 JSON 类型，MySQL 会自动建表为 json 类型
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> knowledgeIds;

    private String voice;
}
