package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.util.StringUtils;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCategoryResponse;
import me.jsjlog.blog.admin.dto.AdminCategorySearchCondition;
import me.jsjlog.blog.post.domain.QCategory;
import me.jsjlog.blog.post.domain.QPost;
import java.util.List;

@RequiredArgsConstructor
public class CategoryRepositoryCustomImpl implements CategoryRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<AdminCategoryResponse> getCategoryListWithPostCount(AdminCategorySearchCondition condition) {

        QCategory qCategory = QCategory.category;
        QPost qPost = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();

        if (!StringUtils.isNullOrEmpty(condition.name())) {
            builder.and(qCategory.name.contains(condition.name()));
        }


        List<AdminCategoryResponse> fetch = jpaQueryFactory.select(Projections.constructor(AdminCategoryResponse.class,
                        qCategory.id,
                        qCategory.name,
                        qCategory.sortOrder,
                        qPost.count(),
                        qCategory.updatedAt
                ))
                .from(qCategory)
                .leftJoin(qPost)
                // 발행 여부와 상관없이 전부 센다.
                // 임시저장·비공개 글도 카테고리를 붙들고 있어서, 있으면 카테고리를 지울 수 없다.
                // 발행글만 세면 화면에는 0편인데 삭제는 막히는 상태가 된다.
                //
                // 글을 거르는 조건이 생기면 where 가 아니라 여기 on 에 붙여야 한다.
                // where 에 두면 글이 없는 카테고리가 통째로 걸러져 LEFT JOIN 이 무의미해진다
                .on(qPost.category.id.eq(qCategory.id))
                .where(builder)
                .groupBy(
                        qCategory.id,
                        qCategory.name,
                        qCategory.sortOrder
                )
                .orderBy(
                        qCategory.sortOrder.asc(),
                        qCategory.id.asc()
                )
                .fetch();

        return fetch;
    }

}
