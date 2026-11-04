CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    image_url TEXT NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    stock_available INTEGER NOT NULL CHECK (stock_available >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO products (id, sku, name, description, image_url, price_paise, stock_available, active) VALUES
('00000000-0000-4000-8000-000000000001', 'BEA-ESS-ESS-001', 'Essence Mascara Lash Princess', 'A popular mascara with a volumizing and lengthening finish.', 'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/1.webp', 89900, 20, TRUE),
('00000000-0000-4000-8000-000000000002', 'BEA-EYE-002', 'Eyeshadow Palette with Mirror', 'A versatile palette for everyday looks, with a built-in mirror.', 'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/1.webp', 129900, 12, TRUE),
('00000000-0000-4000-8000-000000000003', 'BEA-POW-003', 'Powder Canister', 'A lightweight finishing powder for a smooth, natural look.', 'https://cdn.dummyjson.com/product-images/beauty/powder-canister/1.webp', 49900, 18, TRUE),
('00000000-0000-4000-8000-000000000004', 'BEA-LIP-004', 'Red Lipstick', 'A richly pigmented red lipstick with a comfortable finish.', 'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/1.webp', 69900, 25, TRUE),
('00000000-0000-4000-8000-000000000005', 'BEA-NAIL-005', 'Red Nail Polish', 'A glossy red nail polish for a bold, lasting look.', 'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/1.webp', 39900, 30, TRUE);
