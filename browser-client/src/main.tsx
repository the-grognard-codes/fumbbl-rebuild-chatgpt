import { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BoardView } from './board';
import type { RendererTestFault } from './board';
import { decode, newerSnapshot } from './protocol';
import type { Choice, Move, Point, Snapshot, Result } from './protocol';
import './style.css';
import { TeamPanel } from './TeamPanel';
import { MatchPanel } from './MatchPanel';

function App() {
  const [token, setToken] = useState('');
  const [connection, setConnection] = useState('Disconnected');
  const [view, setView] = useState<Snapshot | null>(null);
  const [selected, setSelected] = useState('');
  const [destination, setDestination] = useState<Point>({ x: 6, y: 7 });
  const [log, setLog] = useState<Result[]>([]);
  const [pending, setPending] = useState<string | null>(null);
  const [rendererError, setRendererError] = useState('');
  const [assetFallback, setAssetFallback] = useState('');
  const socket = useRef<WebSocket | null>(null);
  const lastRequest = useRef<{ request: Move | Choice; matchId: string; actor: string } | null>(null);
  const socketGeneration = useRef(0);
  const joinRequestId = useRef<string | null>(null);
  const host = useRef<HTMLDivElement>(null);
  const board = useRef<BoardView | null>(null);
  const [boardReady, setBoardReady] = useState(false);
  const viewRef = useRef(view); viewRef.current = view;
  useEffect(() => {
    let disposed = false;
    const requestedFault = new URLSearchParams(window.location.search).get('rendererFault');
    const testFault: RendererTestFault = import.meta.env.DEV && (requestedFault === 'missing-asset-mapping' || requestedFault === 'asset-load-failure') ? requestedFault : 'none';
    const renderer = new BoardView(testFault);
    const rendererFailed = () => {
      if (disposed) return;
      renderer.destroy();
      if (board.current === renderer) board.current = null;
      setBoardReady(false);
      setRendererError('The pitch renderer stopped. Reload this page to restore graphics; movement controls and fixture state remain available.');
    };
    renderer.mount(host.current!, point => {
      const player = viewRef.current?.players.find(p => p.x === point.x && p.y === point.y);
      if (player) setSelected(player.id); else setDestination(point);
    }, rendererFailed).then(result => {
      if (disposed) renderer.destroy(); else {
        board.current = renderer;
        setAssetFallback(result.assetFallbackMessage ?? '');
        setBoardReady(true);
      }
    }).catch(() => {
      if (!disposed) setRendererError('The pitch could not initialize WebGL. Reload this page after enabling hardware acceleration; movement controls and fixture state remain available.');
    });
    return () => { disposed = true; renderer.destroy(); if (board.current === renderer) board.current = null; socket.current?.close(); };
  }, []);
  useEffect(() => {
    if (!view || !boardReady) return;
    try { board.current?.draw(view, selected); } catch {
      board.current?.destroy();
      board.current = null;
      setBoardReady(false);
      setRendererError('The pitch renderer stopped. Reload this page to restore graphics; movement controls and fixture state remain available.');
    }
  }, [view, selected, boardReady]);
  function connect() {
    if (socket.current) return;
    // A new join is a new session lifetime; its first snapshot must not be compared to the previous one.
    viewRef.current = null; setView(null); setSelected(''); setPending(null); setLog([]);
    const ws = new WebSocket('ws://127.0.0.1:22227/browser/v1');
    const generation = ++socketGeneration.current;
    socket.current = ws; setConnection('Connecting');
    const current = () => socketGeneration.current === generation && socket.current === ws;
    ws.onopen = () => { if (!current()) return; const requestId = crypto.randomUUID(); joinRequestId.current = requestId; setConnection('Authenticating'); ws.send(JSON.stringify({ version: 1, type: 'join', requestId, token })); setToken(''); };
    ws.onmessage = event => {
      if (!current()) return;
      try {
        const message = decode(event.data);
        if (message.type === 'snapshot') {
          const next = newerSnapshot(viewRef.current, message);
          viewRef.current = next;
          setView(next);
          setSelected(current => current || message.players.find(p => p.role === message.actor)?.id || '');
          setConnection('Connected');
        } else {
          setLog(current => [message, ...current].slice(0, 12));
          setPending(current => current === message.requestId ? null : current);
          if (message.requestId === joinRequestId.current && message.status === 'rejected') {
            joinRequestId.current = null;
            setConnection('Authentication failed — re-enter credential to rejoin');
            ws.close();
          }
        }
      } catch { setConnection('Protocol error — rejoin the fixture'); ws.close(); }
    };
    ws.onerror = () => { if (current()) setConnection('Connection failed — check the local stack'); };
    ws.onclose = () => { if (!current()) return; socket.current = null; setConnection('Disconnected — re-enter credential to rejoin'); setPending(null); };
  }
  function disconnect() {
    const ws = socket.current;
    if (!ws) return;
    socket.current = null;
    socketGeneration.current++;
    setPending(null);
    setConnection('Disconnected — re-enter credential to rejoin');
    ws.close();
  }
  function sendMove() {
    if (!view || view.prompt || socket.current?.readyState !== WebSocket.OPEN || pending) return;
    const request: Move = { version: 1, type: 'move', requestId: crypto.randomUUID(), expectedRevision: view.revision, playerId: selected, to: destination };
    lastRequest.current = { request, matchId: view.matchId, actor: view.actor }; setPending(request.requestId); socket.current.send(JSON.stringify(request));
  }
  function sendChoice(optionId: string) {
    if (!view?.prompt || view.prompt.actor !== view.actor || socket.current?.readyState !== WebSocket.OPEN || pending) return;
    const request: Choice = { version: 1, type: 'choice', requestId: crypto.randomUUID(), expectedRevision: view.revision, choiceId: view.prompt.id, optionId };
    lastRequest.current = { request, matchId: view.matchId, actor: view.actor }; setPending(request.requestId); socket.current.send(JSON.stringify(request));
  }
  function repeat() {
    const cached = lastRequest.current;
    if (cached && view && cached.matchId === view.matchId && cached.actor === view.actor && socket.current?.readyState === WebSocket.OPEN && !pending) {
      setPending(cached.request.requestId); socket.current.send(JSON.stringify(cached.request));
    }
  }
  return <main>
    <header><div><span className="eyebrow">LOCAL PLAY LAB · M1c</span><h1>BB2025 movement and choices</h1></div><span className="badge">{connection}</span></header>
    <p className="intro">Synthetic fixture · neutral tokens · adjacent movement and server-owned block choices. Home and away share the same pitch orientation.</p>
    {(!view || !socket.current) && <form onSubmit={e => { e.preventDefault(); connect(); }} className="join"><label>Local session credential<input aria-label="Local session credential" type="password" value={token} onChange={e => setToken(e.target.value)} autoComplete="off" required/></label><button disabled={!!socket.current}>Join fixture</button></form>}
    <section className="summary"><strong>{view ? `You are ${view.actor}` : 'Waiting for fixture'}</strong><span>Turn: {view?.turnOwner ?? '—'}</span><span data-testid="revision">Revision: {view?.revision ?? '—'}</span><span>Pending choice: {view?.prompt ? view.prompt.id : view ? 'none' : '—'}</span></section>
    <div className="layout"><section><div ref={host} className="pitch"/>{rendererError && <p role="alert">{rendererError}</p>}{assetFallback && <p role="status" className="caption">{assetFallback}</p>}
    <p className="caption">26 × 15 · H home / A away · gold ring marks selection · click a token, then a destination.</p>
    {view && <table><caption>Authoritative fixture state</caption><thead><tr><th>Player</th><th>Side</th><th>State</th><th>Square (x, y)</th><th>Movement used</th></tr></thead><tbody>{view.players.map(p => <tr key={p.id}><td>{p.id}</td><td>{p.role}</td><td>{p.state}</td><td>{p.x}, {p.y}</td><td>{p.movementUsed} / {p.movementAllowance}</td></tr>)}</tbody></table>}
    {view && <p className="caption">Rerolls: home {view.resources.home.rerolls} / away {view.resources.away.rerolls} · Home blitz: {view.resources.home.blitzUsed ? 'used' : 'available'} · Pass: {view.resources.home.passUsed ? 'used' : 'available'}</p>}
    </section><aside><h2>Move a token</h2><label>Player<select aria-label="Player" value={selected} onChange={e => setSelected(e.target.value)}>{view?.players.map(p => <option key={p.id} value={p.id}>{p.id} ({p.role})</option>)}</select></label>
    <div className="coordinates"><label>X<input aria-label="Destination X" type="number" value={destination.x} onChange={e => setDestination({ ...destination, x: Number(e.target.value) })}/></label><label>Y<input aria-label="Destination Y" type="number" value={destination.y} onChange={e => setDestination({ ...destination, y: Number(e.target.value) })}/></label></div>
    <button onClick={sendMove} disabled={!view || !!view.prompt || !!pending || connection !== 'Connected'}>Submit move</button>
    {view?.prompt && <section className="choice"><h2>{view.prompt.type} choice</h2><p className="caption">Prompt: {view.prompt.id} · Owner: {view.prompt.actor} · Revision: {view.prompt.revision}</p>{view.prompt.options.map(option => <button key={option.id} onClick={() => sendChoice(option.id)} disabled={view.prompt?.actor !== view.actor || !!pending || connection !== 'Connected'}>{option.label}</button>)}</section>}
    <button className="secondary" onClick={repeat} disabled={!lastRequest.current || !view || lastRequest.current.matchId !== view.matchId || lastRequest.current.actor !== view.actor || !!pending || connection !== 'Connected'}>Repeat last request</button>
    <button className="secondary" onClick={disconnect} disabled={!socket.current}>Disconnect</button>
    <p className="caption">The server validates every request. This diagnostic control also lets the away session demonstrate a rejected move.</p>
    <h2>Request results</h2><div role="log" className="results">{log.map((entry, index) => <article key={`${entry.requestId}-${index}`} className={entry.status}><strong>{entry.status} · {entry.code}</strong><div>Revision {entry.revision}{entry.duplicate ? ' · duplicate' : ''}</div><small>{entry.requestId ?? 'uncorrelated'}</small></article>)}</div></aside></div>
  </main>;
}
createRoot(document.getElementById('root')!).render(window.location.pathname === '/teams' ? <TeamPanel/> : window.location.pathname === '/matches' ? <MatchPanel/> : <><nav><a href="/teams">Team builder</a> · <a href="/matches">Match preparation</a></nav><App/></>);
