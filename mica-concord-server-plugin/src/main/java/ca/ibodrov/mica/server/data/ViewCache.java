package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.api.model.ViewLike.Caching;
import ca.ibodrov.mica.server.data.ViewRenderer.RenderOverrides;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;

import java.time.Duration;
import java.util.function.BiFunction;

public interface ViewCache {

    String DEFAULT_CACHE_ENABLED = "false";
    String DEFAULT_CACHE_TTL = "PT10S";

    static ViewCache inMemory() {
        return new InMemoryViewCache();
    }

    static ViewCache noop() {
        return new NoopViewCache();
    }

    RenderedView getOrRender(RenderRequest request,
                             RenderOverrides overrides,
                             ViewLike viewLike,
                             BiFunction<ViewLike, RenderOverrides, RenderedView> renderer);

    class InMemoryViewCache implements ViewCache {

        private final Cache<CacheKey, RenderedView> cache;

        public InMemoryViewCache() {
            this.cache = Caffeine.newBuilder()
                    .expireAfter(new RenderedViewExpiry())
                    .build();
        }

        public RenderedView getOrRender(RenderRequest request,
                                        RenderOverrides overrides,
                                        ViewLike viewLike,
                                        BiFunction<ViewLike, RenderOverrides, RenderedView> renderer) {
            if (!isCachingEnabled(viewLike)) {
                return renderer.apply(viewLike, overrides);
            }

            var key = new CacheKey(request, overrides);
            return cache.get(key, _key -> renderer.apply(viewLike, overrides));
        }
    }

    class NoopViewCache implements ViewCache {

        @Override
        public RenderedView getOrRender(RenderRequest request,
                                        RenderOverrides overrides,
                                        ViewLike viewLike,
                                        BiFunction<ViewLike, RenderOverrides, RenderedView> renderer) {
            return renderer.apply(viewLike, overrides);
        }
    }

    class RenderedViewExpiry implements Expiry<CacheKey, RenderedView> {

        @Override
        public long expireAfterCreate(CacheKey key, RenderedView value, long currentTime) {
            var ttl = value.view().caching()
                    .map(caching -> caching.ttl().orElse(DEFAULT_CACHE_TTL))
                    .orElse(DEFAULT_CACHE_TTL);
            return Duration.parse(ttl).toNanos();
        }

        @Override
        public long expireAfterUpdate(CacheKey key,
                                      RenderedView value,
                                      long currentTime,
                                      @NonNegative long currentDuration) {
            return 0;
        }

        @Override
        public long expireAfterRead(CacheKey key,
                                    RenderedView value,
                                    long currentTime,
                                    @NonNegative long currentDuration) {
            return 0;
        }
    }

    record CacheKey(RenderRequest request, RenderOverrides overrides) {
    }

    private static boolean isCachingEnabled(ViewLike view) {
        var enabled = view.caching()
                .flatMap(Caching::enabled)
                .orElse(DEFAULT_CACHE_ENABLED);
        return Boolean.parseBoolean(enabled);
    }
}
