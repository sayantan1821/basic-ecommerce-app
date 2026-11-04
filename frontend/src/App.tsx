function App() {
  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/">BASIC<span>FIT</span></a>
        <span className="cart-indicator">Your cart <strong>0</strong></span>
      </header>
      <section className="hero">
        <p className="eyebrow">MOVE WITH INTENT</p>
        <h1>Gear for your<br /><em>next personal best.</em></h1>
        <p className="intro">Thoughtful training essentials, ready when you are.</p>
      </section>
      <section className="catalog" aria-labelledby="catalog-heading">
        <div className="section-heading">
          <div><p className="eyebrow">THE EVERYDAY KIT</p><h2 id="catalog-heading">Shop the essentials</h2></div>
          <span className="catalog-count">Catalog initializing</span>
        </div>
        <div className="empty-state">The product catalog will appear here.</div>
      </section>
      <footer>Built for the Boldfit D2C checkout take-home.</footer>
    </main>
  )
}

export default App
