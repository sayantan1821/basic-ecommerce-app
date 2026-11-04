-- The original fixture has one distinct image per product; its thumbnail is the same photo.
UPDATE products
SET image_urls = ARRAY[image_urls[1]]
WHERE cardinality(image_urls) > 1;
