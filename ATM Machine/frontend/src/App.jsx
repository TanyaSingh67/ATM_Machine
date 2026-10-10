import { useState } from 'react';
import { api } from './api';
import './App.css';

export default function App() {
  const [session, setSession] = useState(null); // { token, name, balance }

  if (!session) return <Login onLogin={setSession} />;
  return <Dashboard session={session} setSession={setSession} />;
}

/* ---------------- Login ---------------- */
function Login({ onLogin }) {
  const [accountNumber, setAccountNumber] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const data = await api('/login', { method: 'POST', body: { accountNumber, pin } });
      onLogin(data);
    } catch (err) {
      setError(err.message);
      setPin('');
    }
  };

  return (
    <div className="screen">
      <form className="card" onSubmit={submit}>
        <h1>WELCOME TO JAVA ATM</h1>
        <label>Account Number</label>
        <input value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} placeholder="e.g. 1001" />
        <label>4-digit PIN</label>
        <input type="password" maxLength={4} value={pin} onChange={(e) => setPin(e.target.value)} />
        <button type="submit">Login</button>
        {error && <p className="error">{error}</p>}
        <p className="hint">Demo: 1001 / 1234</p>
      </form>
    </div>
  );
}

/* ---------------- Dashboard ---------------- */
function Dashboard({ session, setSession }) {
  const { token, name, balance } = session;
  const [view, setView] = useState('home'); // home | withdraw | deposit | statement | pin
  const [amount, setAmount] = useState('');
  const [oldPin, setOldPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [transactions, setTransactions] = useState([]);
  const [msg, setMsg] = useState(null); // { type: 'ok' | 'err', text }

  const open = (v) => {
    setView(v);
    setMsg(null);
    setAmount('');
    setOldPin('');
    setNewPin('');
  };

  const run = async (fn) => {
    try {
      await fn();
    } catch (err) {
      setMsg({ type: 'err', text: err.message });
    }
  };

  const checkBalance = () =>
    run(async () => {
      const data = await api('/balance', { token });
      setSession({ ...session, balance: data.balance });
      setMsg({ type: 'ok', text: `Available balance: Rs. ${data.balance}` });
    });

  const transact = (kind) =>
    run(async () => {
      const data = await api('/' + kind, { method: 'POST', token, body: { amount } });
      setSession({ ...session, balance: data.balance });
      setMsg({ type: 'ok', text: data.message });
      setAmount('');
    });

  const loadStatement = () =>
    run(async () => {
      const data = await api('/statement', { token });
      setTransactions(data.transactions);
      setView('statement');
      setMsg(null);
    });

  const changePin = () =>
    run(async () => {
      const data = await api('/change-pin', { method: 'POST', token, body: { oldPin, newPin } });
      setMsg({ type: 'ok', text: data.message });
      setOldPin('');
      setNewPin('');
    });

  return (
    <div className="screen">
      <div className="card wide">
        <h1>Welcome, {name}!</h1>
        <h2 className="balance">Balance: Rs. {balance}</h2>

        <div className="grid">
          <button onClick={() => { open('home'); checkBalance(); }}>Balance Inquiry</button>
          <button onClick={() => open('withdraw')}>Cash Withdrawal</button>
          <button onClick={() => open('deposit')}>Deposit</button>
          <button onClick={loadStatement}>Mini Statement</button>
          <button onClick={() => open('pin')}>Change PIN</button>
          <button className="danger" onClick={() => setSession(null)}>Logout</button>
        </div>

        <div className="panel">
          {(view === 'withdraw' || view === 'deposit') && (
            <>
              <label>{view === 'withdraw' ? 'Amount to withdraw (multiples of 100)' : 'Amount to deposit'}</label>
              <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
              <button onClick={() => transact(view)}>{view === 'withdraw' ? 'Withdraw' : 'Deposit'}</button>
            </>
          )}

          {view === 'pin' && (
            <>
              <label>Current PIN</label>
              <input type="password" maxLength={4} value={oldPin} onChange={(e) => setOldPin(e.target.value)} />
              <label>New 4-digit PIN</label>
              <input type="password" maxLength={4} value={newPin} onChange={(e) => setNewPin(e.target.value)} />
              <button onClick={changePin}>Update PIN</button>
            </>
          )}

          {view === 'statement' && (
            <table>
              <thead>
                <tr><th>Type</th><th>Amount</th><th>Balance</th><th>Date</th></tr>
              </thead>
              <tbody>
                {transactions.length === 0 && <tr><td colSpan="4">No transactions yet.</td></tr>}
                {transactions.map((t, i) => (
                  <tr key={i}>
                    <td>{t.type}</td>
                    <td>{t.amount}</td>
                    <td>{t.balanceAfter}</td>
                    <td>{new Date(t.date).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {msg && <p className={msg.type === 'ok' ? 'success' : 'error'}>{msg.text}</p>}
        </div>
      </div>
    </div>
  );
}
