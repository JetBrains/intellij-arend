package org.arend.quickfix


class PatternQuickFixTest : QuickFixTestBase() {
    fun `test too many patterns`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat
              | _, _, _, _{-caret-} => 0
        """,
        """
            \func test (x y : Nat) : Nat
              | _, _ => 0
        """)

    fun `test too many patterns no whitespaces`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat
              | _, _, {-caret-}_, {_}=> 0
        """,
        """
            \func test (x y : Nat) : Nat
              | _, _ => 0
        """)

    fun `test too many implicit patterns`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat
              | _, _, {-caret-}{_}, _ => 0
        """,
        """
            \func test (x y : Nat) : Nat
              | _, _ => 0
        """)

    fun `test too many patterns elim`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat \elim x
              | _, {-caret-}_ => 0
        """,
        """
            \func test (x y : Nat) : Nat \elim x
              | _ => 0
        """)

    fun `test too many patterns no vars`() = simpleQuickFixTest("Remove",
        """
            \func test : Nat
              | _, 0 => 3
              | {-caret-}_, 1 => 4
              | _, _ => 5
        """,
        """
            \func test : Nat
        """)

    fun `test too many patterns no vars with braces`() = simpleQuickFixTest("Remove",
        """
            \func test : Nat \with {
              | {-caret-}_, _ => 0
            }
        """,
        """
            \func test : Nat
        """)

    fun `test too many patterns no vars constructor elim`() = simpleQuickFixTest("Remove",
        """
            \data D
              | con1
              | con2 \with {
                | {-caret-}_ => con1
              }
        """,
        """
            \data D
              | con1
              | con2
        """)

    fun `test too many patterns data no vars`() = simpleQuickFixTest("Remove",
        """
            \data D \with
              | {-caret-}_ => con
              | _ => con2
        """,
        """
            \data D
        """)

    fun `test too many patterns constructor`() = simpleQuickFixTest("Remove",
        """
            \data D | con Nat
            \func test (d : D) : Nat
              | con _  _ 2{-caret-} => 0
        """,
        """
            \data D | con Nat
            \func test (d : D) : Nat
              | con _ => 0
        """)

    fun `test too many patterns no vars constructor`() = simpleQuickFixTest("Remove",
        """
            \data D | con
            \func test (d : D) (x : Nat) : Nat
              | con {-caret-}_ _, _ => 0
        """,
        """
            \data D | con
            \func test (d : D) (x : Nat) : Nat
              | con, _ => 0
        """)

    fun `test too many patterns implicit`() = simpleQuickFixTest("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _, _{-caret-} => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _ => 0
        """)


    fun `test elim implicit ok`() = checkNoQuickFixes("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | _, _, _{-caret-} => 0
        """)

    fun `test remove implicit`() = simpleQuickFixTest("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{_}, _, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _ => 0
        """)

    fun `test make explicit`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{_}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, {_}, _ => 0
        """)

    fun `test make explicit complex`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{suc _}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc _, {_}, _ => 0
        """)

    fun `test make explicit id`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{zero}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | zero, {_}, _ => 0
        """)

    fun `test make explicit in constructor`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc {-caret-}{_}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc _, {_}, _ => 0
        """)

    fun `test make explicit complex in constructor`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc {-caret-}{suc _}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc (suc _), {_}, _ => 0
        """)

    fun `test make explicit id in constructor`() = simpleQuickFixTest("Make pattern explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc {-caret-}{zero}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | suc zero, {_}, _ => 0
        """)

    fun `test single implicit`() {
        configure(
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat
                  | {-caret-}{_} => 0
            """)
        checkNoQuickFixes("Remove")
        checkQuickFix("Make pattern explicit",
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat
                  | _ => 0
            """)
    }

    fun `test implicit with elim`() {
        configure(
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
                  | _, {-caret-}{_}, _ => 0
            """)
        checkNoQuickFixes("Remove")
        checkQuickFix("Make pattern explicit",
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
                  | _, _, _ => 0
            """)
    }

    fun `test many implicits with elim`() {
        configure(
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
                  | {0}, 1, 2 => 1
                  | 2, 3, {-caret-}{4} => 2
                  | _, {_}, _ => 0
            """)
        checkNoQuickFixes("Remove")
        checkQuickFix("Make pattern explicit",
            """
                \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
                  | 0, 1, 2 => 1
                  | 2, 3, 4 => 2
                  | _, _, _ => 0
            """)
    }

    fun `test removal of as pattern`() = typedQuickFixTest("Remove",
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as{-caret-} x, zero, zero => {?} 
            """,
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | (), zero, zero => {?} 
            """)

    fun `test removal of as pattern and surrounding parentheses` () = typedQuickFixTest("Remove",
            """
               \data Empty 
                
               \data D
                 | con Empty Nat

               \func func (d : D) : Nat
                 | con (() \as x{-caret-}) (suc n) => 0 
            """, """
               \data Empty 
                
               \data D
                 | con Empty Nat

               \func func (d : D) : Nat
                 | con () (suc n) => 0 
            """)

    fun `test removing redundant pattern`() = typedQuickFixTest("Remove",
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, zero{-caret-}, zero => {?} 
            """,
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, _, zero => {?} 
            """)

    fun `test removing redundant pattern implicit`() = typedQuickFixTest("Remove",
            """
               \data Empty

               \func lol (m : Empty) (n : Nat) {k : Nat} : Nat
                 | () \as x, _, {zero}{-caret-} => {?} 
            """, """
               \data Empty

               \func lol (m : Empty) (n : Nat) {k : Nat} : Nat
                 | () \as x, _, {_} => {?} 
            """)

    fun `test removing pattern inside constructor pattern`() = typedQuickFixTest("Remove",
            """
               \data Empty
                
               \data D
                 | con Empty Nat

               \func func (d : D) : Nat
                 | con (() \as x) (suc n{-caret-}) => 0 
            """, """
               \data Empty 
                
               \data D
                 | con Empty Nat

               \func func (d : D) : Nat
                 | con (() \as x) _ => 0  
            """)

    fun `test removing pattern right hand side`() = typedQuickFixTest("Remove",
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, zero, zero => {- foo -} 101{-caret-} 
            """,
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, zero, zero
            """)

    fun `test removing redundant clause` () = typedQuickFixTest("Remove",
            """
               \func foo (n : Nat) : Nat
                 | zero => 0
                 | suc _ => 1
                 | {- foo -} suc (suc _) => {-caret-} 2 
            """, """
               \func foo (n : Nat) : Nat
                 | zero => 0
                 | suc _ => 1 
            """)

    fun `test removing pattern right hand side on arrow with empty goal`() = typedQuickFixTest("Remove",
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, zero, zero =>{-caret-}  {?} 
            """,
            """
               \data Empty

               \func lol (m : Empty) (n k : Nat) : Nat
                 | () \as x, zero, zero
            """)
}