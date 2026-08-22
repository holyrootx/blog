package me.jsjlog.blog.post.domain;

import me.jsjlog.blog.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false, unique = true)
    private Long sortOrder;

    public Category(String name, Long sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void update(String name, Long sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
