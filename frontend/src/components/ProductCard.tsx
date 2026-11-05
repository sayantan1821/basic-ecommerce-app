import type { Product } from '../types/store'
import { formatPrice } from '../utils/formatPrice'

type ProductCardProps = {
  product: Product
  index: number
  quantity: number
  imageIndex: number
  onChangeImage: (product: Product, direction: number) => void
  onSetQuantity: (product: Product, quantity: number) => void
  onAdd: (product: Product) => void
}

export function ProductCard({ product, index, quantity, imageIndex, onChangeImage, onSetQuantity, onAdd }: ProductCardProps) {
  const stockLabel = product.available === 0 ? 'Sold out' : product.available <= 5 ? 'Only a few left' : ''
  return <article className="product-card">
    <div className={`product-image tone-${index % 5}`}>
      <img src={product.imageUrls[imageIndex] ?? product.imageUrls[0]} alt={`${product.name} image ${imageIndex + 1}`} loading="lazy" onError={event => { event.currentTarget.style.opacity = '0' }} />
      {product.imageUrls.length > 1 && <>
        <button className="carousel-arrow previous" aria-label={`Previous ${product.name} image`} onClick={() => onChangeImage(product, -1)}>‹</button>
        <button className="carousel-arrow next" aria-label={`Next ${product.name} image`} onClick={() => onChangeImage(product, 1)}>›</button>
        <span className="carousel-position">{imageIndex + 1} / {product.imageUrls.length}</span>
      </>}
    </div>
    <div className="product-info">
      <div className="product-title-row"><h3>{product.name}</h3><strong className="product-price">{formatPrice(product.pricePaise)}</strong></div>
      <p className="product-description">{product.description}</p>
      <div className="product-actions">
        <span className={`stock ${product.available === 0 ? 'out' : product.available <= 5 ? 'low-stock' : ''}`}>{stockLabel}</span>
        {quantity ? <div className="quantity-control">
          <button aria-label={`Decrease ${product.name} quantity`} onClick={() => onSetQuantity(product, quantity - 1)}>−</button>
          <span>{quantity}</span>
          <button aria-label={`Increase ${product.name} quantity`} disabled={quantity >= Math.min(product.available, 10)} onClick={() => onSetQuantity(product, quantity + 1)}>+</button>
        </div> : <button className="add-button" disabled={!product.available} onClick={() => onAdd(product)}>Add to bag <span>+</span></button>}
      </div>
    </div>
  </article>
}
