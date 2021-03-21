package org.arend.quickfix

import org.arend.ArendTestBase

class InstanceInferenceQuickFixTest: QuickFixTestBase() {

    fun testBasic() = typedQuickFixTest("Specify", """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B{-caret-} 0 
    """, """
       \open M
        
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B 0 
    """)
}