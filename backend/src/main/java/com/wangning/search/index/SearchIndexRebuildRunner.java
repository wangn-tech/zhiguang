package com.wangning.search.index;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 由环境变量显式启用的启动期搜索索引回灌器。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = {"enabled", "rebuild-on-startup"}, havingValue = "true")
public class SearchIndexRebuildRunner implements ApplicationRunner {

    private final SearchIndexRebuilder searchIndexRebuilder;

    /**
     * 执行一次全量回灌。
     *
     * @param args 应用启动参数
     * @throws Exception 回灌失败时抛出，使显式启用的部署任务失败
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        searchIndexRebuilder.rebuild();
    }
}
