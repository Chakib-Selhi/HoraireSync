import { createContext, useContext, useReducer } from 'react';
import { initialState, reducer } from './reducer.js';

const ScheduleContext = createContext(null);

export function ScheduleProvider({ children, initial = initialState }) {
  const [state, dispatch] = useReducer(reducer, initial);
  return <ScheduleContext.Provider value={{ state, dispatch }}>{children}</ScheduleContext.Provider>;
}

export function useSchedule() {
  const value = useContext(ScheduleContext);
  if (!value) throw new Error('useSchedule must be used inside ScheduleProvider');
  return value;
}
