-- Wish list: students can save courses/workshops and marketplace originals for later.
-- Safe to run more than once.
CREATE TABLE IF NOT EXISTS wishlist_items (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    item_type  VARCHAR(20) NOT NULL CHECK (item_type IN ('course', 'painting')),
    item_id    BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL,
    CONSTRAINT wishlist_items_unique UNIQUE (user_id, item_type, item_id),
    CONSTRAINT wishlist_items_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
