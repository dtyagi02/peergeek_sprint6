import {
  cloneElement,
  createContext,
  Fragment,
  isValidElement,
  useContext,
} from "react";
import { slotRegistry } from "./slotRegistry";

const SlotRegistryContext = createContext(slotRegistry);

export function SlotRegistryProvider({ registry, children }) {
  return (
    <SlotRegistryContext.Provider value={registry}>
      {children}
    </SlotRegistryContext.Provider>
  );
}

export function Slot({ name, ...props }) {
  const registry = useContext(SlotRegistryContext);
  const contributions = registry[name] ?? [];
  if (contributions.length === 0) return null;

  return (
    <>
      {contributions.map((contribution, index) => {
        const key = contribution.id ?? `${name}-${index}`;
        if (typeof contribution.component === "function") {
          const Component = contribution.component;
          return <Component key={key} {...props} />;
        }
        if (isValidElement(contribution.element)) {
          return Object.keys(props).length > 0 ? (
            cloneElement(contribution.element, { key, ...props })
          ) : (
            <Fragment key={key}>{contribution.element}</Fragment>
          );
        }
        if (typeof contribution.element === "function") {
          const Render = contribution.element;
          return <Render key={key} {...props} />;
        }
        return <Fragment key={key}>{contribution.element}</Fragment>;
      })}
    </>
  );
}
