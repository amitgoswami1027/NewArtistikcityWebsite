-- Room photos for the "See it on your wall" preview (/purchase/step-2), managed in Admin > Marketplace > Room preview.
-- The area (percent of the photo) is where a piece hangs; area_width_in is how wide that area is in real life, so the
-- piece is drawn to scale (NULL = no scale: the piece is fitted into the area).
CREATE TABLE room_scenes (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    label         VARCHAR(120)  NOT NULL,
    note          VARCHAR(255)  NULL,
    photo         VARCHAR(500)  NOT NULL,
    photo_width   INT           NOT NULL,
    photo_height  INT           NOT NULL,
    area_x        DECIMAL(6,3)  NOT NULL,
    area_y        DECIMAL(6,3)  NOT NULL,
    area_w        DECIMAL(6,3)  NOT NULL,
    area_h        DECIMAL(6,3)  NOT NULL,
    area_width_in DECIMAL(7,2)  NULL,
    sort_order    INT           NOT NULL DEFAULT 0,
    status        VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'HIDDEN')),
    created_at    DATETIME      NULL,
    updated_at    DATETIME      NULL
);
CREATE INDEX room_scenes_status_sort_index ON room_scenes (status, sort_order);

-- the four rooms that shipped with the site (1200 x 800 renders, 5.05 photo pixels per inch on the back wall)
INSERT INTO room_scenes (label, note, photo, photo_width, photo_height, area_x, area_y, area_w, area_h, area_width_in, sort_order, status, created_at, updated_at) VALUES
('Living room', 'Above an 86-inch sofa', '/assets/rooms/living.jpg', 1200, 800, 16.667, 13.400, 66.667, 49.100, 158.40, 1, 'ACTIVE', NOW(), NOW()),
('Industrial loft', 'Above an 80-inch console', '/assets/rooms/studio.jpg', 1200, 800, 16.667, 13.400, 66.667, 51.600, 158.40, 2, 'ACTIVE', NOW(), NOW()),
('Bedroom', 'Above a queen bed', '/assets/rooms/bedroom.jpg', 1200, 800, 20.833, 12.500, 58.333, 38.750, 138.60, 3, 'ACTIVE', NOW(), NOW()),
('Gallery wall', 'Centred at eye level', '/assets/rooms/gallery.jpg', 1200, 800, 16.667, 13.400, 66.667, 63.500, 158.40, 4, 'ACTIVE', NOW(), NOW());

INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at) VALUES
('room_preview', 'enabled', '1', NOW(), NOW());
