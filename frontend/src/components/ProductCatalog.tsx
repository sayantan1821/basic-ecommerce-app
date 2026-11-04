import type { Product } from '../types/store'
import { ProductCard } from './ProductCard'

type ProductCatalogProps = {
  products: Product[]
  cart: Record<string, number>
  imageIndexes: Record<string, number>
  loading: boolean
  error: string
  onRetry: () => void
  onChangeImage: (product: Product, direction: number) => void
  onSetQuantity: (product: Product, quantity: number) => void
  onAdd: (product: Product) => void
}

export function ProductCatalog({ products, cart, imageIndexes, loading, error, onRetry, onChangeImage, onSetQuantity, onAdd }: ProductCatalogProps) {
  return <section className="catalog" id="catalog">
    <div className="section-heading"><div><p className="eyebrow">THE EVERYDAY KIT</p><h2>Shop the essentials</h2></div>{!loading && !error && <span className="catalog-count">{products.length} considered essentials</span>}</div>
    {loading && <div className="status-panel" role="status">Loading the collection…</div>}
    {!loading && error && <div className="status-panel" role="alert"><p>We couldn’t load the collection.</p><button className="text-button" onClick={onRetry}>Try again</button><small>{error}</small></div>}
    {!loading && !error && products.length === 0 && <div className="status-panel">No products are available right now.</div>}
    {!loading && !error && products.length > 0 && <div className="product-grid">{products.map((product, index) => <ProductCard
      key={product.id}
      product={product}
      index={index}
      quantity={cart[product.id] ?? 0}
      imageIndex={imageIndexes[product.id] ?? 0}
      onChangeImage={onChangeImage}
      onSetQuantity={onSetQuantity}
      onAdd={onAdd}
    />)}</div>}
  </section>
}
