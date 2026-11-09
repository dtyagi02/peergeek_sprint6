import { Component } from "react";
import { ErrorState } from "../../shared/components/ErrorState";

export class ErrorBoundary extends Component {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="mx-auto max-w-3xl px-4 py-16">
          <ErrorState
            description="Please refresh the page and try again."
            title="Something went wrong"
          />
        </main>
      );
    }
    return this.props.children;
  }
}
