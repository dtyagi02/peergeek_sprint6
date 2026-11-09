import { FormProvider, useForm } from "react-hook-form";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { EmptyState } from "./EmptyState";
import { ErrorState } from "./ErrorState";
import { FormField } from "./FormField";
import { Input } from "./Input";
import { Label } from "./Label";
import { Spinner } from "./Spinner";
import { ToastProvider } from "./Toast";
import { useToast } from "./useToast";

function FormHarness() {
  const form = useForm({ defaultValues: { name: "" } });
  return (
    <FormProvider {...form}>
      <form>
        <FormField label="Your name" name="name" />
      </form>
    </FormProvider>
  );
}

function ToastHarness() {
  const { notify } = useToast();
  return (
    <button
      onClick={() => notify({ title: "Saved", description: "Changes saved" })}
    >
      Show toast
    </button>
  );
}

describe("shared components", () => {
  it("renders an accessible Button with disabled behavior", () => {
    render(<Button disabled>Continue</Button>);
    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();
  });

  it("renders Input and associates Label with it", () => {
    render(
      <>
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" />
      </>,
    );
    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
  });

  it("connects FormField to React Hook Form", async () => {
    const user = userEvent.setup();
    render(<FormHarness />);
    const input = screen.getByRole("textbox", { name: "Your name" });

    await user.type(input, "Ada");

    expect(input).toHaveValue("Ada");
  });

  it("opens an accessible Dialog from its trigger", async () => {
    const user = userEvent.setup();
    render(
      <Dialog
        description="Dialog description"
        title="Dialog title"
        trigger={<Button>Open dialog</Button>}
      >
        Dialog content
      </Dialog>,
    );
    await user.click(screen.getByRole("button", { name: "Open dialog" }));
    expect(screen.getByRole("dialog", { name: "Dialog title" })).toBeVisible();
    expect(screen.getByText("Dialog content")).toBeVisible();
  });

  it("announces toast notifications", async () => {
    render(
      <ToastProvider>
        <ToastHarness />
      </ToastProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Show toast" }));
    expect(await screen.findByText("Saved")).toBeInTheDocument();
    expect(screen.getByText("Changes saved")).toBeInTheDocument();
  });

  it("announces loading state with Spinner", () => {
    render(<Spinner label="Saving" />);
    expect(screen.getByRole("status", { name: "Saving" })).toBeInTheDocument();
  });

  it("renders EmptyState and ErrorState messages", () => {
    render(
      <>
        <EmptyState description="No records yet" title="Nothing here" />
        <ErrorState description="Try again later" title="Unable to load" />
      </>,
    );
    expect(screen.getByRole("heading", { name: "Nothing here" })).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("Try again later");
  });
});
