import { useEffect, useRef, useState } from 'react';
import { decode } from './protocol.ts';
import { canPlaceReserve, decodeSetupState } from './setup-protocol.ts';
import type { SetupCode, SetupState } from './setup-protocol.ts';
import './SetupPanel.css';

type Request = Record<string, unknown>;
type Retained = { request: Request; subject: string; matchId: string };
const endpoint = 'ws://127.0.0.1:22227/browser/v1';
const explanation = (code: SetupCode) => ({
  ILLEGAL_SETUP: 'Field 11 available players, at least three on the line of scrimmage, at most two in each wide zone, and your selected captain if any.',
  ILLEGAL_PLACEMENT: 'Select an empty square in your half.',
  WRONG_ACTOR: 'The other participant owns this decision.',
  STALE_REVISION: 'The setup changed. Reload its current state before submitting another action.',
  SESSION_UNAVAILABLE: 'This activated session is unavailable. Setup cannot recover after a server restart or engine failure. Prepare a new match to play again.',
  NOT_ACTIVATED: 'Activate this match from match preparation first.',
} as Partial<Record<SetupCode, string>>)[code] ?? 'The server rejected this request.';

export function SetupPanel() {
  const [matchId, setMatchId] = useState(() => new URLSearchParams(location.search).get('matchId') ?? '');
  const [token, setToken] = useState('');
  const [status, setStatus] = useState('Disconnected');
  const [error, setError] = useState('');
  const [view, setView] = useState<SetupState | null>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [last, setLast] = useState<Retained | null>(null);
  const [subject, setSubject] = useState('');
  const [playerId, setPlayerId] = useState('');
  const [actionId, setActionId] = useState('');
  const [x, setX] = useState(0); const [y, setY] = useState(0);
  const socket = useRef<WebSocket | null>(null);
  const currentView = useRef<SetupState | null>(null);
  const pendingId = useRef<string | null>(null);
  const loadId = useRef(''); const selectedMatch = useRef(matchId);
  useEffect(() => () => socket.current?.close(), []);
  const connected = status === 'Connected';
  const own = view?.players.filter(player => player.role === view.callerRole) ?? [];
  const maySetup = connected && !pending && view?.phase === 'SETUP' && view.actor === view.callerRole;
  const availableActions = view?.actions.filter(action => action.actor === view.callerRole) ?? [];
  const mayAct = connected && !pending && availableActions.some(action => action.id === actionId);
  const actionsByKind = availableActions.reduce<Record<string, typeof availableActions>>((groups, action) => {
    (groups[action.kind] ??= []).push(action);
    return groups;
  }, {});
  function load(ws = socket.current) {
    if (!ws || ws.readyState !== WebSocket.OPEN || !selectedMatch.current) return;
    loadId.current = crypto.randomUUID();
    ws.send(JSON.stringify({ version: 1, type: 'setup', operation: 'load', requestId: loadId.current, matchId: selectedMatch.current }));
  }
  function connect() {
    if (socket.current) return;
    selectedMatch.current = matchId;
    const ws = new WebSocket(endpoint); socket.current = ws;
    setStatus('Connecting'); setSubject(''); currentView.current = null; setView(null);
    const current = () => socket.current === ws;
    ws.onopen = () => { if (current()) ws.send(JSON.stringify({ version: 1, type: 'join', requestId: crypto.randomUUID(), token })); };
    ws.onmessage = event => {
      if (!current()) return;
      try {
        const type = JSON.parse(event.data).type;
        if (type !== 'setupState') {
          const message = decode(event.data);
          if (message.type === 'snapshot') { setSubject(message.actor); setStatus('Connected'); setToken(''); load(ws); }
          else if (message.status === 'rejected') { setError(message.code); ws.close(); }
          return;
        }
        const message = decodeSetupState(event.data);
        const isLoad = message.requestId === loadId.current;
        const isAction = message.requestId !== null && message.requestId === pendingId.current;
        if (message.requestId !== null && !isLoad && !isAction) return;
        if (message.state && message.state.matchId === selectedMatch.current) {
          if (!currentView.current || message.state.revision >= currentView.current.revision) {
            currentView.current = message.state; setView(message.state);
          }
        }
        if (isAction) { pendingId.current = null; setPending(null); }
        if (message.code === 'SESSION_UNAVAILABLE' || message.code === 'NOT_FOUND') {
          currentView.current = null; setView(null); pendingId.current = null; setPending(null); setLast(null);
        }
        setError(message.code === 'ACCEPTED' ? '' : `${message.code}: ${explanation(message.code)}`);
      } catch {
        setError('Invalid server response. Reconnect to reload authoritative setup.'); ws.close();
      }
    };
    ws.onclose = () => { if (current()) { socket.current = null; setStatus('Disconnected'); } };
    ws.onerror = () => { if (current()) setError('Connection failed. Check the local server.'); };
  }
  function mutate(operation: string, fields: Request = {}) {
    if (!view || !connected || pendingId.current || socket.current?.readyState !== WebSocket.OPEN) return;
    const request = { version: 1, type: 'setup', operation, requestId: crypto.randomUUID(), matchId: view.matchId, expectedRevision: view.revision, ...fields };
    pendingId.current = request.requestId; setPending(request.requestId); setLast({ request, subject, matchId: view.matchId });
    socket.current.send(JSON.stringify(request));
  }
  function retry() {
    if (!last || !view || !connected || last.subject !== subject || last.matchId !== view.matchId) return;
    pendingId.current = String(last.request.requestId); setPending(pendingId.current);
    socket.current?.send(JSON.stringify(last.request));
  }
  return <main className="team-builder setup-panel">
    <nav><a href="/matches">Match preparation</a> · <a href="/teams">Team builder</a> · <a href="/">Board scenarios</a></nav>
    <h1>Match setup</h1>
    <p>Play with the frozen teams. Server restart ends this setup session; in-progress recovery is not available.</p>
    <form onSubmit={event => { event.preventDefault(); connect(); }}>
      <label>Local credential <input aria-label="Local credential" type="password" value={token} onChange={event => setToken(event.target.value)} autoComplete="off" required /></label>
      <label>Match ID <input aria-label="Match ID" value={matchId} disabled={!!socket.current} onChange={event => setMatchId(event.target.value)} required /></label>
      <button disabled={!!socket.current || !token || !matchId}>Join setup</button>
      <button type="button" className="secondary" onClick={() => socket.current?.close()} disabled={!socket.current}>Disconnect</button>
    </form>
    <p role="status">{status}</p>{error && <p role="alert">{error}</p>}
    <button type="button" className="secondary" onClick={() => load()} disabled={!connected}>Reload setup snapshot</button>
    {view && <section aria-label="Authoritative setup">
      <h2>{view.phase.replaceAll('_', ' ').toLowerCase()}</h2>
      <p data-testid="setup-status">Revision {view.revision} · you are {view.callerRole} · decision owner {view.actor} · turn {view.turn} ({view.turnMode}) · weather {view.weather} · rerolls home {view.homeRerolls}, away {view.awayRerolls}</p>
      <p>Ball {view.ball ? `${view.ball.x}, ${view.ball.y}` : 'off pitch'} · active player {view.activePlayerId ?? 'none'}</p>
      {view.prompt && <section aria-label="Pre-match choice"><h3>{view.prompt.kind === 'coin' ? 'Call the coin toss' : 'Choose to receive or kick'}</h3>
        {view.prompt.options.map(option => <button key={option} type="button" onClick={() => mutate('choice', { promptId: view.prompt!.id, optionId: option })} disabled={!connected || !!pending || view.prompt!.actor !== view.callerRole}>{option}</button>)}
      </section>}
      {view.phase === 'READY_FOR_KICKOFF' && <p>Both teams have confirmed legal setups. The kicking participant can choose a server-issued kick target.</p>}
      {view.phase === 'PLAY' && view.actions.length === 0 && <p role="alert">This engine decision does not yet have browser controls. The match remains in memory; reconnecting will preserve this decision.</p>}
      {view.actions.length > 0 && <section aria-label="Server actions" className="server-actions">
        <h3>Server actions</h3>
        <p>{availableActions.length ? 'Choose an action issued for your team. Its actor and kind are shown in the list.' : 'The server has not issued an action for your team.'}</p>
        <label>Action <select aria-label="Server action" value={actionId} onChange={event => setActionId(event.target.value)} disabled={!connected || !!pending || availableActions.length === 0}>
          <option value="">Select</option>{Object.entries(actionsByKind).map(([kind, actions]) => <optgroup key={kind} label={kind}>{actions.map(action => <option key={action.id} value={action.id}>{action.label} · {action.actor} · {action.kind}</option>)}</optgroup>)}
        </select></label>
        <button type="button" onClick={() => mutate('action', { actionId })} disabled={!mayAct}>Execute action</button>
      </section>}
      <p>Home H: x 0–12 · Away A: x 13–25. Line of scrimmage: x 12/13, y 4–10. Wide zones: y 0–3 and 11–14.</p>
      <div className="setup-grid" role="group" aria-label="Pitch grid">
        {Array.from({ length: 15 }, (_, row) => Array.from({ length: 26 }, (_, column) => {
          const player = view.players.find(item => item.x === column && item.y === row);
          const legal = !!playerId && canPlaceReserve(view, playerId, column, row);
          return <button key={`${column},${row}`} type="button" className={`${player?.role ?? ''} ${column === 12 || column === 13 ? 'los' : ''} ${row < 4 || row > 10 ? 'wide' : ''} ${legal && maySetup ? 'legal' : ''}`}
            aria-label={`Square ${column}, ${row}${player ? ` ${player.role} ${player.name}` : ''}`} onClick={() => { setX(column); setY(row); if (player?.role === view.callerRole) setPlayerId(player.id); }} disabled={!connected}>
            {player ? `${player.role === 'home' ? 'H' : 'A'}${player.slot}` : '·'}
          </button>;
        }))}
      </div>
      {view.phase === 'SETUP' && <section aria-label="Placement controls"><h3>Set up {view.actor}</h3>
        <label>Player <select aria-label="Setup player" value={playerId} onChange={event => setPlayerId(event.target.value)} disabled={!maySetup}><option value="">Select</option>{own.map(player => <option key={player.id} value={player.id}>{player.name} #{player.slot}{player.x === null ? ' reserve' : ''}</option>)}</select></label>
        <label>X <input aria-label="Setup X" type="number" min="0" max="25" value={x} onChange={event => setX(Number(event.target.value))} disabled={!maySetup} /></label>
        <label>Y <input aria-label="Setup Y" type="number" min="0" max="14" value={y} onChange={event => setY(Number(event.target.value))} disabled={!maySetup} /></label>
        <button type="button" onClick={() => mutate('place', { playerId, to: { x, y } })} disabled={!maySetup || !canPlaceReserve(view, playerId, x, y)}>Place on empty own-half square</button>
        <button type="button" className="secondary" onClick={() => mutate('place', { playerId, to: null })} disabled={!maySetup || !own.some(player => player.id === playerId && player.x !== null)}>Return selected player to reserve</button>
        <button type="button" onClick={() => mutate('confirm')} disabled={!maySetup}>Confirm legal setup</button>
      </section>}
      {pending && <p>Waiting for the action result. After reconnect, explicitly retry the retained request to reconcile it.</p>}
      {last && <button type="button" className="secondary" onClick={retry} disabled={!connected || last.subject !== subject || last.matchId !== view.matchId}>Repeat last setup request</button>}
      <table><caption>Frozen team players</caption><thead><tr><th>Player</th><th>Role</th><th>State</th><th>Square</th></tr></thead><tbody>{view.players.map(player => <tr key={player.id}><td>{player.name}</td><td>{player.role}</td><td>{player.state}</td><td>{player.x === null ? 'reserve' : `${player.x}, ${player.y}`}</td></tr>)}</tbody></table>
    </section>}
  </main>;
}
