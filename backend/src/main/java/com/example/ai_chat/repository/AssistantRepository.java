package com.example.ai_chat.repository;

import com.example.ai_chat.entity.Assistant;
import org.springframework.data.jpa.repository.JpaRepository;

// 继承 JpaRepository，第一个参数是实体类，第二个参数是主键类型
// 只要继承了，增删改查的方法 Spring 底层就自动帮你实现了，不用写一行 SQL！
public interface AssistantRepository extends JpaRepository<Assistant, String> {

}
