package ru.finex.evolution;

/**
 * Contains auto-migration marks.
 *
 * @author m0nster.mind
 * @see Evolution
 */
public interface Schemas {

    @Evolution("auth")
    interface AuthSchema { }

    @Evolution("logic")
    interface LogicSchema { }

}
