import { useEffect, useRef, useState } from "react";
import { getMentionSuggestions } from "./communityApi";
import { UserAvatar } from "./UserAvatar";

const MENTION_TRIGGER_REGEX = /(?:^|\s)@([a-zA-Z0-9_]*)$/;

export function MentionTextarea({
  id,
  value,
  onChange,
  placeholder = "Write something...",
  maxLength = 2000,
  minRows = 3,
  disabled = false,
  className = "",
  autoFocus = false,
  showCharCount = true,
  onSubmitShortcut,
  submitOnEnter = false,
  textareaClassName = "",
}) {
  const textareaRef = useRef(null);
  const dropdownRef = useRef(null);

  const [suggestions, setSuggestions] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [activeMention, setActiveMention] = useState(null); // { query, startIndex, endIndex }

  // Check cursor position and detect @mention trigger
  function handleInputOrSelection(explicitText, explicitCursor) {
    const el = textareaRef.current;
    const text = explicitText !== undefined ? explicitText : (el?.value ?? value ?? "");
    const rawCursor = explicitCursor !== undefined ? explicitCursor : el?.selectionStart;
    const cursor = typeof rawCursor === "number" && rawCursor >= 0 ? rawCursor : text.length;
    const textBeforeCursor = text.slice(0, cursor);

    const match = textBeforeCursor.match(MENTION_TRIGGER_REGEX);
    if (match) {
      const query = match[1];
      // Index where '@' character is located
      const atIndex = textBeforeCursor.length - query.length - 1;
      setActiveMention({ query, startIndex: atIndex, endIndex: cursor, currentText: text });
    } else {
      setActiveMention(null);
      setIsOpen(false);
    }
  }

  // Fetch suggestions when activeMention query changes
  useEffect(() => {
    if (!activeMention) {
      setIsOpen(false);
      setSuggestions([]);
      return;
    }

    let isSubscribed = true;
    const timer = setTimeout(async () => {
      try {
        const results = await getMentionSuggestions(activeMention.query);
        if (isSubscribed) {
          setSuggestions(results || []);
          setIsOpen((results && results.length > 0) || false);
          setSelectedIndex(0);
        }
      } catch {
        if (isSubscribed) {
          setSuggestions([]);
          setIsOpen(false);
        }
      } finally {
        if (isSubscribed) {
        }
      }
    }, 100);

    return () => {
      isSubscribed = false;
      clearTimeout(timer);
    };
  }, [activeMention?.query]);

  // Insert selected mention into textarea
  function insertMention(suggestion) {
    if (!activeMention || !textareaRef.current) return;

    const baseText = activeMention.currentText ?? value;
    const before = baseText.slice(0, activeMention.startIndex);
    const after = baseText.slice(activeMention.endIndex);
    const mentionText = `@${suggestion.username} `;
    const newValue = before + mentionText + after;

    if (newValue.length > maxLength) return;

    onChange(newValue);
    setIsOpen(false);
    setActiveMention(null);

    // Place cursor right after the inserted mention + space
    const newCursor = activeMention.startIndex + mentionText.length;
    requestAnimationFrame(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.setSelectionRange(newCursor, newCursor);
      }
    });
  }

  // Keyboard navigation for autocomplete list
  function handleKeyDown(e) {
    if (isOpen && suggestions.length > 0) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % suggestions.length);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
        return;
      }
      if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        insertMention(suggestions[selectedIndex]);
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        setIsOpen(false);
        return;
      }
    }

    if (submitOnEnter && e.key === "Enter" && !e.shiftKey && onSubmitShortcut) {
      e.preventDefault();
      onSubmitShortcut();
      return;
    }

    // Ctrl+Enter or Cmd+Enter to submit
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter" && onSubmitShortcut) {
      e.preventDefault();
      onSubmitShortcut();
    }
  }

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target) &&
        textareaRef.current &&
        !textareaRef.current.contains(event.target)
      ) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const charsRemaining = maxLength - (value?.length || 0);
  const isNearLimit = charsRemaining < 50;

  return (
    <div className={`relative w-full ${className}`}>
      <textarea
        id={id}
        ref={textareaRef}
        rows={minRows}
        disabled={disabled}
        autoFocus={autoFocus}
        value={value}
        maxLength={maxLength}
        placeholder={placeholder}
        onChange={(e) => {
          const newVal = e.target.value;
          onChange(newVal);
          const cursor = typeof e.target.selectionStart === "number" && e.target.selectionStart > 0
            ? e.target.selectionStart
            : newVal.length;
          handleInputOrSelection(newVal, cursor);
        }}
        onKeyUp={() => handleInputOrSelection()}
        onClick={() => handleInputOrSelection()}
        onKeyDown={handleKeyDown}
        className={`w-full resize-y rounded-lg border border-neutral-300 bg-white px-3.5 py-2.5 text-sm text-[#171717] placeholder-neutral-400 transition-all focus:border-[#06110C] focus:outline-none focus:ring-2 focus:ring-[#06110C]/20 disabled:cursor-not-allowed disabled:bg-neutral-50 ${textareaClassName}`}
      />

      {/* Autocomplete dropdown */}
      {isOpen && suggestions.length > 0 && (
        <div
          ref={dropdownRef}
          role="listbox"
          aria-label="User mention suggestions"
          className="absolute left-0 z-50 mt-1 max-h-56 w-72 overflow-y-auto rounded-lg border border-neutral-200 bg-white py-1 shadow-lg ring-1 ring-black/5 animate-in fade-in slide-in-from-top-1"
        >
          <div className="px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-neutral-400 border-b border-neutral-100">
            Matching Users
          </div>
          {suggestions.map((suggestion, idx) => {
            const isSelected = idx === selectedIndex;
            return (
              <button
                type="button"
                key={suggestion.id}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setSelectedIndex(idx)}
                onClick={() => insertMention(suggestion)}
                className={`flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors ${
                  isSelected
                    ? "bg-[#06110C]/5 text-[#171717]"
                    : "text-neutral-700 hover:bg-neutral-50"
                }`}
              >
                <UserAvatar author={suggestion} size="sm" />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium text-[#171717]">
                    {suggestion.displayName || suggestion.username}
                  </div>
                  <div className="truncate text-xs text-[#A31C1B]">
                    @{suggestion.username}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* Character count */}
      {showCharCount && (
        <div className="mt-1 flex items-center justify-end px-1">
          <span
            className={`text-xs ${
              isNearLimit ? "font-semibold text-[#A31C1B]" : "text-neutral-400"
            }`}
          >
            {value.length} / {maxLength}
          </span>
        </div>
      )}
    </div>
  );
}
