import { Application, Graphics, Sprite, Text, Texture } from 'pixi.js';
import type { Snapshot, Point } from './protocol';

export type RendererTestFault = 'none' | 'missing-asset-mapping' | 'asset-load-failure';

export interface BoardMountResult {
  assetFallbackMessage: string | null;
}

const MAX_ASSET_ATTEMPTS_PER_RESOURCE = 1;
const MARKER_ASSET_URL = '/diagnostic/token-marker.svg';

/** Rendering owns no rules, network connection, or authoritative game state. */
export class BoardView {
  private app: Application | null = null;
  private abortController: AbortController | null = null;
  private canvas: HTMLCanvasElement | null = null;
  private host: HTMLElement | null = null;
  private contextLostHandler: ((event: Event) => void) | null = null;
  private markerTexture: Texture | null = null;
  private useVectorFallback = false;
  private destroyed = false;
  private renderSequence = 0;

  constructor(private readonly testFault: RendererTestFault = 'none', private readonly applicationFactory: () => Application = () => new Application()) {}

  async mount(host: HTMLElement, onSquare: (point: Point) => void, onRendererFailure: () => void): Promise<BoardMountResult> {
    if (this.destroyed) throw new Error('Cannot mount a destroyed board');
    const app = this.applicationFactory();
    this.app = app;
    this.host = host;
    this.abortController = new AbortController();
    try {
      await app.init({ width: 936, height: 540, background: '#18332d', preference: ['webgl'], antialias: true });
      if (this.destroyed) {
        this.destroyApplication(app);
        throw new Error('Board was destroyed while WebGL initialized');
      }
      this.canvas = app.canvas;
      this.contextLostHandler = event => {
        event.preventDefault();
        onRendererFailure();
      };
      this.canvas.addEventListener('webglcontextlost', this.contextLostHandler, { once: true });
      this.canvas.setAttribute('aria-label', '26 by 15 pitch; use the player and destination controls for keyboard movement');
      host.appendChild(this.canvas);
      app.stage.eventMode = 'static';
      app.stage.hitArea = app.screen;
      app.stage.on('pointertap', event => onSquare({ x: Math.floor(event.global.x / 36), y: Math.floor(event.global.y / 36) }));
      const assetFallbackMessage = await this.loadMarkerAsset();
      this.useVectorFallback = assetFallbackMessage !== null;
      return { assetFallbackMessage };
    } catch (error) {
      this.destroy();
      throw error;
    }
  }

  private async loadMarkerAsset(): Promise<string | null> {
    const url = this.testFault === 'missing-asset-mapping'
      ? undefined
      : this.testFault === 'asset-load-failure' ? '/renderer-test-missing-marker.svg' : MARKER_ASSET_URL;
    if (!url) return 'Token marker asset mapping is unavailable; using vector fallback.';
    for (let attempt = 0; attempt < MAX_ASSET_ATTEMPTS_PER_RESOURCE; attempt++) {
      try {
        const response = await fetch(url, { signal: this.abortController?.signal });
        if (!response.ok || !response.headers.get('content-type')?.includes('image/svg+xml')) throw new Error(`Asset returned ${response.status}`);
        const objectUrl = URL.createObjectURL(await response.blob());
        const image = new Image();
        try {
          image.src = objectUrl;
          await image.decode();
        } finally { URL.revokeObjectURL(objectUrl); }
        if (this.destroyed) throw new Error('Board was destroyed while marker asset decoded');
        this.markerTexture = Texture.from(image);
        return null;
      } catch (error) {
        if (this.abortController?.signal.aborted) throw error;
      }
    }
    return 'Token marker asset could not load; using vector fallback.';
  }

  draw(view: Snapshot, selected: string) {
    const app = this.app;
    if (!app || this.destroyed) return;
    app.stage.removeChildren().forEach(child => child.destroy());
    const grid = new Graphics();
    for (let x = 0; x < 26; x++) for (let y = 0; y < 15; y++) {
      grid.rect(x * 36, y * 36, 36, 36).fill((x + y) % 2 ? 0x1d3b33 : 0x214138).stroke({ color: 0x608075, alpha: 0.3, width: 1 });
    }
    grid.moveTo(468, 0).lineTo(468, 540).stroke({ color: 0xbbcfc0, alpha: 0.65, width: 2 });
    app.stage.addChild(grid);
    for (const p of view.players) {
      const color = p.role === 'home' ? 0xe5d5ac : 0x87bce0;
      if (this.markerTexture && !this.useVectorFallback) {
        const token = new Sprite(this.markerTexture);
        token.anchor.set(0.5);
        token.position.set(p.x * 36 + 18, p.y * 36 + 18);
        token.width = 26; token.height = 26; token.tint = color;
        app.stage.addChild(token);
      } else {
        const token = new Graphics().circle(p.x * 36 + 18, p.y * 36 + 18, 13)
          .fill(color)
          .stroke({ color: selected === p.id ? 0xffd263 : 0x132322, width: selected === p.id ? 4 : 2 });
        app.stage.addChild(token);
      }
      if (selected === p.id) app.stage.addChild(new Graphics().circle(p.x * 36 + 18, p.y * 36 + 18, 15).stroke({ color: 0xffd263, width: 4 }));
      const label = new Text({ text: `${this.useVectorFallback ? 'F ' : ''}${p.role === 'home' ? 'H' : 'A'}`, style: { fontFamily: 'sans-serif', fontSize: this.useVectorFallback ? 10 : 14, fontWeight: 'bold', fill: 0x132322 } });
      label.anchor.set(0.5); label.position.set(p.x * 36 + 18, p.y * 36 + 18); app.stage.addChild(label);
    }
    if (view.ball) app.stage.addChild(new Graphics().ellipse(view.ball.x * 36 + 18, view.ball.y * 36 + 18, 6, 9).fill(0xdb9362).stroke({ color: 0xffffff, width: 1 }));
    app.render();
    if (this.host) {
      this.host.dataset.renderedMatchId = view.matchId;
      this.host.dataset.renderedRevision = String(view.revision);
      this.host.dataset.renderSequence = String(++this.renderSequence);
    }
  }
  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this.abortController?.abort();
    if (this.canvas && this.contextLostHandler) this.canvas.removeEventListener('webglcontextlost', this.contextLostHandler);
    this.contextLostHandler = null;
    this.canvas = null;
    if (this.host) {
      delete this.host.dataset.renderedMatchId;
      delete this.host.dataset.renderedRevision;
      delete this.host.dataset.renderSequence;
    }
    this.host = null;
    const app = this.app;
    this.app = null;
    this.destroyApplication(app);
    this.markerTexture?.destroy(true);
    this.markerTexture = null;
  }

  private destroyApplication(app: Application | null) {
    if (app?.renderer) app.destroy(true, { children: true });
  }
}
