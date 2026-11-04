ALTER TABLE products ADD COLUMN image_urls TEXT[];

UPDATE products
SET image_urls = ARRAY[
    image_url,
    REPLACE(image_url, '/1.webp', '/thumbnail.webp')
];

ALTER TABLE products ALTER COLUMN image_urls SET NOT NULL;
ALTER TABLE products DROP COLUMN image_url;
