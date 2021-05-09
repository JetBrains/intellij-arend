package org.arend.quickfix

class InstanceInferenceQuickFixTest: QuickFixTestBase() {

    fun testBasic() = typedQuickFixTest("Import", """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B{-caret-} 0 
    """, """
       \open M (Nat-X)
        
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B 0 
    """)

    fun testBasic2() = typedQuickFixTest("Import", """
       \open M ()
       
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B{-caret-} 0 
    """, """
       \open M (Nat-X)
        
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }
       
       \func f (t : M.T) => M.B 0 
    """)

    fun testBasic3() = typedQuickFixTest("Import", """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }

       \class D {
         \func f (t : M.T) => M.B{-caret-} 1
       } 
    """, """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }

       \class D {
         \func f (t : M.T) => M.B 1
       } \where {
         \open M (Nat-X)
       }
    """)

    fun testBasic4() = typedQuickFixTest("Import", """
       --! A.ard
       \import Main
       
       \class M \where {         
         \instance Nat-X : X | A => Nat | B => \lam x => x         
       }
       --! Main.ard
       \class X (A : \Type0) {
         | B : A -> Nat
       }
       
       \func T => B 0 = 0
       
       \func f (t : T) => B{-caret-} 1
    """, """
       \import A
       \open M (Nat-X)
       
       \class X (A : \Type0) {
         | B : A -> Nat
       }
       
       \func T => B 0 = 0
       
       \func f (t : T) => B 1
    """)

    fun testBasic5() = typedQuickFixTest("Import", """
       \open C
       \class C
         | field : Nat
       
       \module M \where {
         \instance I : C
           | field => 0
       }
       
       \func func => field{-caret-}
    """, """
      \open C
      \open M (I)
      
      \class C
        | field : Nat
       
      \module M \where {
        \instance I : C
          | field => 0
      }
      
      \func func => field          
    """)

    fun testAddInstanceArgument1() = typedQuickFixTest("Add", """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }

       \class D {
         \func f (t : M.T) => M.B{-caret-} 1
       }  
    """, """
       \class M \where {
         \class X (A : \Type0) {
           | B : A -> Nat
         }
         \instance Nat-X : X | A => Nat | B => \lam x => x
         \func T => B 0 = 0
       }

       \class D {
         \func f {x : M.X Nat} (t : M.T) => M.B 1
       }   
    """)

    fun testAddInstanceArgument2() = typedQuickFixTest("Add", """
       \class C (A : \Type)
         | foo : A -> A
  
       \func test {A : \Type} (a : A) => foo{-caret-} a 
    """, """
       \class C (A : \Type)
         | foo : A -> A
  
       \func test {A : \Type} {c : C A} (a : A) => foo a 
    """)

    fun testNoInstanceQuickFixes() = checkNoQuickFixes("", """
       \class C (A : \Type)
         | foo : A -> A
         
       \func test => foo{-caret-}
    """)
}