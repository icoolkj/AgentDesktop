/**
 * Defer destructive cleanup on unmount so Vue HMR / fast remounts do not tear down
 * long-lived resources (PTY, native handles, etc.).
 */
export function useSurvivingUnmount(delayMs = 300) {
  let generation = 0

  function bumpMount(): number {
    generation += 1
    return generation
  }

  function scheduleTeardown(generationAtUnmount: number, teardown: () => void): void {
    window.setTimeout(() => {
      if (generationAtUnmount === generation) {
        teardown()
      }
    }, delayMs)
  }

  return { bumpMount, scheduleTeardown }
}
